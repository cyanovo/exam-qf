<template>
  <section class="exam-camera" aria-label="考试摄像头">
    <header>
      <span><el-icon><VideoCamera /></el-icon> 摄像头</span>
      <span class="camera-status" :class="{ live: state === 'live' }" role="status">{{ statusText }}</span>
    </header>
    <div class="camera-preview">
      <video ref="video" autoplay muted playsinline aria-label="本机摄像头画面" />
      <div v-if="state !== 'live'" class="camera-placeholder">
        <el-icon :size="26"><VideoCamera /></el-icon>
        <span>{{ message }}</span>
      </div>
    </div>
    <el-button v-if="state !== 'live' && state !== 'requesting'" :icon="Refresh" size="small" @click="start">
      重新开启
    </el-button>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, onDeactivated, onActivated, ref } from 'vue'
import { VideoCamera, Refresh } from '@element-plus/icons-vue'

const video = ref(null)
const state = ref('requesting')
const message = ref('请在浏览器中允许使用摄像头')
const statusText = computed(() => ({
  requesting: '等待授权', live: '已开启', error: '未开启', muted: '画面暂停'
}[state.value]))
let stream = null
let generation = 0
let disposed = false
let pending = false

function release() {
  if (stream) {
    for (const track of stream.getTracks()) {
      track.onended = track.onmute = track.onunmute = null
      track.stop()
    }
    stream = null
  }
  if (video.value) {
    video.value.onloadedmetadata = null
    video.value.srcObject = null
  }
}

async function start() {
  if (pending || disposed) return
  release()
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    state.value = 'error'
    message.value = '摄像头需要 HTTPS 或本机地址，请检查访问地址与浏览器。'
    return
  }
  const requestId = ++generation
  pending = true
  state.value = 'requesting'
  message.value = '请在浏览器中允许使用摄像头'
  try {
    const acquired = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false
    })
    // A permission prompt may resolve after the exam page has already closed.
    if (disposed || requestId !== generation) {
      acquired.getTracks().forEach(track => track.stop())
      return
    }
    stream = acquired
    const track = stream.getVideoTracks()[0]
    if (!track || track.readyState === 'ended') throw new Error('Disconnected')
    track.onended = () => {
      release()
      state.value = 'error'
      message.value = '摄像头已断开，请检查设备后重新开启。'
    }
    track.onmute = () => {
      state.value = 'muted'
      message.value = '摄像头暂时没有画面，请检查设备是否被占用。'
    }
    track.onunmute = () => { state.value = 'live' }
    video.value.onloadedmetadata = async () => {
      try {
        await video.value.play()
        if (!disposed && requestId === generation && stream) {
          state.value = track.muted ? 'muted' : 'live'
          if (track.muted) message.value = '摄像头暂时没有画面，请检查设备。'
        }
      } catch {
        if (disposed || requestId !== generation) return
        release()
        state.value = 'error'
        message.value = '画面播放失败，请重新开启摄像头。'
      }
    }
    video.value.srcObject = stream
  } catch (error) {
    if (disposed || requestId !== generation) return
    console.warn('[ExamCamera] Camera could not start:', error)
    release()
    state.value = 'error'
    message.value = ({
      NotAllowedError: '摄像头权限未获允许，请在浏览器网站设置中允许后重试。',
      NotFoundError: '未检测到摄像头，请连接设备后重试。',
      NotReadableError: '摄像头无法使用，请关闭占用它的程序后重试。',
      AbortError: '摄像头启动中断，请重新开启。',
      SecurityError: '浏览器禁止访问摄像头，请检查网站权限。'
    })[error.name] || '摄像头开启失败，请检查设备与浏览器权限后重试。'
  } finally {
    if (requestId === generation) pending = false
  }
}

function close() {
  disposed = true
  generation++
  release()
}

function pageHide() {
  generation++
  pending = false
  release()
  state.value = 'error'
  message.value = '摄像头已关闭，请重新开启。'
}

onMounted(() => {
  window.addEventListener('pagehide', pageHide)
  start()
})
onDeactivated(close)
onActivated(() => {
  if (disposed) {
    disposed = false
    pending = false
    start()
  }
})
onBeforeUnmount(() => {
  close()
  window.removeEventListener('pagehide', pageHide)
})
</script>

<style scoped>
.exam-camera { background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-light, #e4e7ed); border-radius: 8px; padding: 12px; flex-shrink: 0; }
header { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 14px; margin-bottom: 10px; }
header > span:first-child { display: flex; align-items: center; gap: 4px; }
.camera-status { color: #a15c00; font-size: 12px; }
.camera-status.live { color: #16804a; }
.camera-preview { aspect-ratio: 4 / 3; width: 100%; position: relative; background: #16191d; border-radius: 4px; overflow: hidden; }
video { width: 100%; height: 100%; object-fit: contain; transform: scaleX(-1); }
.camera-placeholder { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; padding: 12px; background: #16191d; color: #f0f2f5; font-size: 12px; line-height: 1.6; text-align: center; overflow-wrap: anywhere; }
.exam-camera > .el-button { margin-top: 8px; }
@media (max-width: 760px) {
  .exam-camera { display: grid; grid-template-columns: minmax(0, 144px) minmax(0, 1fr); gap: 8px 12px; }
  header { grid-column: 1 / -1; margin: 0; }
  .camera-preview { grid-row: 2; }
  .el-button { justify-self: start; align-self: end; margin: 0; }
  .camera-placeholder { font-size: 11px; padding: 6px; gap: 2px; }
}
</style>
