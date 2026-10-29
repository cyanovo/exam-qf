<template>
  <div class="wait-container">
    <div v-if="status === 'none'" class="empty">
      <el-empty description="暂无考试安排" />
    </div>
    <div v-else-if="status === 'ongoing'" class="ongoing">
      <h2>{{ examName }}</h2>
      <p>考试正在进行中</p>
      <el-button type="primary" @click="enterExam">进入考试</el-button>
    </div>
    <div v-else-if="status === 'upcoming'" class="upcoming">
      <h2>{{ examName }}</h2>
      <p>距离考试开始还有：</p>
      <div class="countdown">{{ countdown }}</div>
      <el-button type="success" :disabled="!canEnter" @click="enterExam">进入考试</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const status = ref('loading')
const examId = ref(null)
const examName = ref('')
const startTime = ref(null)
const endTime = ref(null)
const now = ref(new Date())
let timer = null

const countdown = computed(() => {
  if (!startTime.value) return '--:--:--'
  const diff = new Date(startTime.value).getTime() - now.value.getTime()
  if (diff <= 0) return '00:00:00'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  return `${h}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}`
})

const canEnter = computed(() => {
  if (!startTime.value || !endTime.value) return false
  const s = new Date(startTime.value).getTime()
  const e = new Date(endTime.value).getTime()
  return now.value.getTime() >= s && now.value.getTime() <= e
})

function fetchWaitInfo() {
  request.get('/exam/student/waitInfo').then(res => {
    const data = res.data
    status.value = data.status
    if (data.status === 'ongoing' || data.status === 'upcoming') {
      examId.value = data.examId
      examName.value = data.examName
      startTime.value = data.startTime || null
      endTime.value = data.endTime || null
    }
  })
}

function enterExam() {
  if (examId.value) {
    router.push(`/exam/answer?examId=${examId.value}`)
  }
}

onMounted(() => {
  fetchWaitInfo()
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.wait-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: 40px 20px;
}

.empty, .ongoing, .upcoming {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: 0 8px 32px rgba(0, 137, 123, 0.08);
  border: 1px solid rgba(0, 137, 123, 0.1);
  max-width: 480px;
  width: 100%;
  text-align: center;
}

.ongoing h2, .upcoming h2 {
  font-size: 22px;
  font-weight: 700;
  color: #00695c;
  margin: 0 0 12px;
}

.ongoing p, .upcoming p {
  font-size: 15px;
  color: #64748b;
  margin: 0 0 24px;
}

.countdown {
  font-size: 56px;
  font-weight: 700;
  color: #00897b;
  margin: 20px 0;
  font-variant-numeric: tabular-nums;
  letter-spacing: 2px;
}

:deep(.el-button--primary) {
  border-radius: 14px;
  padding: 12px 32px;
  font-size: 15px;
  font-weight: 600;
}

:deep(.el-button--success) {
  border-radius: 14px;
  padding: 12px 32px;
  font-size: 15px;
  font-weight: 600;
}
</style>