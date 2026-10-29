<template>
  <section class="python-workbench">
    <div class="toolbar">
      <el-button :disabled="disabled || busy" icon="CaretRight" @click="submit('SAMPLE')">运行样例</el-button>
      <el-button :disabled="disabled || busy" type="primary" @click="submit('FORMAL')">正式提交</el-button>
      <el-select v-model="theme" aria-label="编辑器主题" class="theme">
        <el-option label="浅色" value="vs" /><el-option label="深色" value="vs-dark" />
      </el-select>
      <el-input-number v-model="fontSize" :min="12" :max="24" aria-label="字号" controls-position="right" />
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div ref="host" class="editor" aria-label="Python代码编辑器"></div>
    <div class="samples">
      <div v-for="(sample, index) in samples" :key="index">
        <h4>公开样例 {{ index + 1 }}</h4>
        <div class="sample-grid"><div><b>输入</b><pre>{{ sample.input }}</pre></div><div><b>预期输出</b><pre>{{ sample.expected }}</pre></div></div>
      </div>
    </div>
    <h4>提交记录</h4>
    <el-table :data="history" size="small">
      <el-table-column prop="id" label="提交编号" width="100" />
      <el-table-column label="类型" width="100"><template #default="{ row }">{{ row.mode === 'SAMPLE' ? '公开样例' : '正式提交' }}</template></el-table-column>
      <el-table-column label="状态" min-width="160"><template #default="{ row }">{{ states[row.state] || row.state }}</template></el-table-column>
      <el-table-column prop="receivedAt" label="接收时间" min-width="160" />
    </el-table>
    <section v-if="feedback.length" class="output">
      <div v-for="item in feedback" :key="item.case">
        <h4>样例 {{ item.case }} · {{ item.passed ? '通过' : '未通过' }} · 状态 {{ item.status }}</h4>
        <pre>{{ item.stdout || '(无标准输出)' }}</pre><pre v-if="item.stderr || item.compileOutput" class="error-output">{{ item.stderr }}{{ item.compileOutput }}</pre>
      </div>
    </section>
  </section>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import * as monaco from 'monaco-editor/editor/editor.api.js'
import 'monaco-editor/languages/definitions/python/register.js'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import request from '@/utils/request'

self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
const props = defineProps({ modelValue: { type: String, default: '' }, examId: [String, Number], questionId: [String, Number],
  snapshotHash: String, samples: { type: Array, default: () => [] }, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const host = ref(null), busy = ref(false), error = ref(''), history = ref([]), feedback = ref([])
const theme = ref('vs'), fontSize = ref(15)
const states = { QUEUED: '排队中', RUNNING: '评测中', RETRY: '系统重试中', SUCCEEDED: '评测完成', FAILED: '系统失败 · 等待重试' }
let editor, timer, disposed = false, pending = null
watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value) })
watch(() => props.disabled, value => editor?.updateOptions({ readOnly: value }))
watch(fontSize, value => editor?.updateOptions({ fontSize: value }))
watch(theme, value => monaco.editor.setTheme(value))

async function refresh() {
  try {
    const response = await request.get(`/exam/code/${props.examId}/history/${props.questionId}`)
    if (disposed) return
    history.value = response.data
    feedback.value = history.value.find(row => row.mode === 'SAMPLE' && row.feedback)?.feedback || []
  } catch { if (!disposed) error.value = '提交记录加载失败，稍后自动重试' }
  finally { if (!disposed) timer = setTimeout(refresh, 4000) }
}
async function submit(mode) {
  if (busy.value || props.disabled) return
  busy.value = true
  error.value = ''
  try {
    if (!crypto.subtle || !crypto.randomUUID) throw new Error('代码提交需要 HTTPS 安全连接')
    if (!props.modelValue.trim()) throw new Error('请先填写代码')
    const source = props.modelValue
    if (new TextEncoder().encode(source).length > 60000) throw new Error('代码不能超过60KB')
    if (mode === 'FORMAL') await ElMessageBox.confirm('本次代码将作为不可修改的正式提交保存，以截止前最后一次正式提交计分。', '确认正式提交')
    if (props.disabled) throw new Error('考试已截止或正在交卷')
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(source))
    const sourceHash = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('')
    if (!pending || pending.sourceHash !== sourceHash || pending.mode !== mode) {
      pending = { questionId: props.questionId, mode, source, sourceHash, snapshotHash: props.snapshotHash, key: crypto.randomUUID() }
    }
    const response = await request.post(`/exam/code/${props.examId}`, pending, { headers: { repeatSubmit: false } })
    pending = null
    history.value = [response.data, ...history.value.filter(row => row.id !== response.data.id)]
    ElMessage.success(`已接收提交 ${response.data.id}`)
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '提交失败，可重试'
  } finally { busy.value = false }
}
onMounted(() => {
  editor = monaco.editor.create(host.value, { value: props.modelValue, language: 'python', theme: theme.value,
    fontSize: fontSize.value, readOnly: props.disabled, automaticLayout: true, minimap: { enabled: false },
    scrollBeyondLastLine: false, tabSize: 4, wordWrap: 'on' })
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor.getValue()))
  refresh()
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
  const model = editor?.getModel()
  editor?.dispose()
  model?.dispose()
})
</script>
<style scoped>
.python-workbench { min-width: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.toolbar .el-button { margin-left: 0; }.theme { width: 100px; }.toolbar .el-input-number { width: 100px; }
.editor { height: 400px; min-width: 0; border: 1px solid #aaa; }
.sample-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
h4 { margin: 18px 0 10px; font-size: 14px; } pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: #f3f4f5; }
.error-output { color: #ab2630; } .output { border-top: 1px solid #ddd; }
</style>
