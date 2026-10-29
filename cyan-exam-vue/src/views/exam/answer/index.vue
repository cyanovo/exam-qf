<template>
  <div class="exam-answer">
    <!-- 左侧：题目区域 -->
    <div class="question-panel">
      <div class="exam-header">
        <h2>{{ examName }}</h2>
        <div class="question-progress">{{ currentIndex + 1 }} / {{ questions.length }}</div>
      </div>

      <div class="question-card" v-if="currentQuestion">
        <div class="question-type-badge">
          {{ typeLabel(currentQuestion.questionType) }}
        </div>
        <h3 class="question-title">{{ currentQuestion.title }}</h3>

        <!-- 单选题 -->
        <div v-if="['SINGLE_CHOICE', 'JUDGE'].includes(currentQuestion.questionType)" class="options-list">
          <div
            v-for="opt in currentQuestion.options"
            :key="opt.label"
            class="option-item"
            :class="{ 'selected': localAnswer === opt.label }"
            @click="selectSingleOption(opt.label)"
          >
            <div class="option-radio" :class="{ 'checked': localAnswer === opt.label }">
              <span v-if="localAnswer === opt.label" class="radio-dot"></span>
            </div>
            <div class="option-content">
              <span class="option-label">{{ opt.label }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </div>
        </div>

        <!-- 多选题 -->
        <div v-else-if="currentQuestion.questionType === 'MULTI_CHOICE'" class="options-list">
          <div
            v-for="opt in currentQuestion.options"
            :key="opt.label"
            class="option-item"
            :class="{ 'selected': multiSelected.includes(opt.label) }"
            @click="toggleMultiOption(opt.label)"
          >
            <div class="option-checkbox" :class="{ 'checked': multiSelected.includes(opt.label) }">
              <span v-if="multiSelected.includes(opt.label)" class="check-icon">✓</span>
            </div>
            <div class="option-content">
              <span class="option-label">{{ opt.label }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </div>
        </div>

        <PythonWorkbench v-else-if="currentQuestion.questionType === 'PYTHON'"
          :key="currentQuestion.questionId" :model-value="localAnswer"
          :exam-id="examId" :question-id="currentQuestion.questionId" :disabled="!canEdit"
          :samples="currentQuestion.samples" :snapshot-hash="currentQuestion.snapshotHash"
          @update:model-value="value => { localAnswer = value; onTextInput(value) }" />
        <!-- 填空/简答题 -->
        <div v-else class="text-answer">
          <textarea
            v-model="localAnswer"
            :disabled="!canEdit"
            class="answer-textarea"
            placeholder="不同的空用;隔开"
            @input="onTextInput($event.target.value)"
            @blur="onAnswerChange"
          ></textarea>
        </div>
      </div>

      <div class="nav-buttons">
        <button class="nav-btn" :disabled="currentIndex === 0" @click="prevQuestion">
          <span class="arrow">←</span> 上一题
        </button>
        <button class="nav-btn" :disabled="currentIndex === questions.length - 1" @click="nextQuestion">
          下一题 <span class="arrow">→</span>
        </button>
      </div>
    </div>

    <!-- 右侧：信息面板 -->
    <div class="info-panel">
      <div class="timer-card">
        <div class="timer-label">剩余时间</div>
        <div class="timer-value">{{ remainingTime }}</div>
        <el-button type="primary" :disabled="!loaded || confirming || finishing || finished" @click="finishExam(false)">
          {{ finished ? '已交卷' : '交卷' }}
        </el-button>
        <div v-if="savingAnswer" class="save-status">正在保存答案…</div>
        <div v-if="saveError || dirty" class="save-error">
          {{ expired ? '存在未保存答案，已截止，以服务器记录为准' : '答案尚未保存' }}
          <el-button v-if="canEdit" link type="primary" :disabled="savingAnswer" @click="saveCurrentAnswer">
            重试保存
          </el-button>
        </div>
        <div v-else-if="loaded && !savingAnswer" class="save-status">答案已保存</div>
        <div v-if="finishError" class="save-error">交卷未确认，请重试交卷</div>
      </div>

      <div class="question-nav-card">
        <div class="nav-title">答题卡</div>
        <div class="question-grid">
          <button
            v-for="(q, idx) in questions"
            :key="q.questionId"
            class="q-btn"
            :class="{ 'current': currentIndex === idx, 'answered': q.userAnswer }"
            @click="switchQuestion(idx)"
          >
            {{ idx + 1 }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAnswerSaver } from './useAnswerSaver'
const PythonWorkbench = defineAsyncComponent(() => import('./PythonWorkbench.vue'))

const route = useRoute()
const router = useRouter()
const examId = ref(route.query.examId)
const examName = ref('')
const questions = ref([])
const currentIndex = ref(0)
const endTime = ref(null)
const now = ref(new Date())
let timer = null
let autosaveTimer = null
let inputTimer = null
let switching = false
let disposed = false
const loaded = ref(false)
const finished = ref(false)
const finishing = ref(false)
const confirming = ref(false)
const finishError = ref(false)
const expired = computed(() => endTime.value && now.value.getTime() >= endTime.value.getTime())
const canEdit = computed(() => loaded.value && !finished.value && !finishing.value && !expired.value)
const { stage, flush, saving: savingAnswer, error: saveError, dirty } = useAnswerSaver(
  (questionId, answer) => {
    if (finished.value || expired.value || disposed) return Promise.reject(new Error('Answer editing closed'))
    return request.post('/exam/student/submitAnswer', {
      examId: examId.value, questionId, answer
    }, { headers: { repeatSubmit: false } })
  }
)

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const localAnswer = ref('')
const multiSelected = ref([])   // 多选题临时选中的标签数组

const remainingTime = computed(() => {
  if (!endTime.value) return '--:--:--'
  const diff = new Date(endTime.value).getTime() - now.value.getTime()
  if (diff <= 0) return '00:00:00'
  const seconds = Math.ceil(diff / 1000)
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const typeLabel = (type) => {
  const map = {
    SINGLE_CHOICE: '单选题',
    MULTI_CHOICE: '多选题',
    JUDGE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
    PYTHON: 'Python 编程题'
  }
  return map[type] || type
}

async function fetchQuestions() {
  try {
    const session = await request.post(`/exam/student/${examId.value}/session`, null, {
      headers: { repeatSubmit: false }
    })
    if (session.data.status !== 'IN_PROGRESS') {
      finished.value = true
      ElMessage.info('这场考试已交卷')
      return
    }
  } catch (e) {
    ElMessage.error('无法进入考试')
    return
  }
  request.get('/exam/student/questions', { params: { examId: examId.value } }).then(res => {
    const data = res.data
    examName.value = data.examName || ''
    questions.value = data.questions || []
    endTime.value = data.endTime ? new Date(data.endTime) : null
    loaded.value = true
    if (questions.value.length > 0) {
      // 初始化当前题目的答案显示
      loadAnswerForCurrent()
    }
  }).catch(() => {
    alert('获取试卷失败，请重试')
  })
}

// 根据当前题目类型加载答案到 localAnswer 或 multiSelected
function loadAnswerForCurrent() {
  const q = questions.value[currentIndex.value]
  if (!q) return
  if (q.questionType === 'MULTI_CHOICE') {
    // 多选题：将逗号分隔的字符串转为数组
    multiSelected.value = q.userAnswer ? q.userAnswer.split(',').filter(s => s) : []
    localAnswer.value = ''
  } else {
    localAnswer.value = q.userAnswer || ''
    if (q.questionType === 'JUDGE' && !q.options?.some(option => option.label === localAnswer.value)) {
      const matches = (q.options || []).filter(option => option.content === localAnswer.value)
      if (matches.length === 1) localAnswer.value = matches[0].label
    }
    multiSelected.value = []
  }
}

function stageCurrentAnswer() {
  if (!currentQuestion.value || finished.value) return
  stage(currentQuestion.value, currentQuestion.value.questionType === 'MULTI_CHOICE'
    ? multiSelected.value.join(',') : localAnswer.value)
}

function saveCurrentAnswer() {
  clearTimeout(inputTimer)
  stageCurrentAnswer()
  return flush()
}

async function finishExam(auto = false) {
  if (!loaded.value || finishing.value || finished.value || disposed) return
  if (!auto) {
    if (confirming.value) return
    confirming.value = true
    try {
      await ElMessageBox.confirm('交卷后无法继续修改答案，确认交卷？', '确认交卷')
    } catch {
      return
    } finally {
      confirming.value = false
    }
  }
  if (finishing.value || finished.value || disposed) return
  finishing.value = true
  finishError.value = false
  clearTimeout(inputTimer)
  try {
    if (!expired.value) {
      if (!await saveCurrentAnswer()) {
        ElMessage.error('当前答案未保存，交卷已取消')
        return
      }
    } else if (savingAnswer.value) {
      await flush()
    }
    await request.post(`/exam/student/${examId.value}/finish`, null, {
      headers: { repeatSubmit: false }
    })
    finished.value = true
    if (dirty.value) {
      ElMessage.warning('已到时交卷，未保存的答案未计入，请联系监考老师')
    } else {
      ElMessage.success('交卷成功')
    }
    router.replace('/exam/wait')
  } catch {
    finishError.value = true
    ElMessage.error('交卷失败，请重试')
  } finally {
    finishing.value = false
  }
}

// 单选题/判断题选择
async function selectSingleOption(label) {
  if (!canEdit.value || switching) return
  localAnswer.value = label
  await saveCurrentAnswer()
}

// 多选题切换选项
async function toggleMultiOption(label) {
  if (!canEdit.value || switching) return
  const index = multiSelected.value.indexOf(label)
  if (index >= 0) {
    multiSelected.value.splice(index, 1)
  } else {
    multiSelected.value.push(label)
  }
  await saveCurrentAnswer()
}

// 非选择题的答案变化（失焦保存）
async function onAnswerChange() {
  if (!canEdit.value) return
  await saveCurrentAnswer()
}

function onTextInput(answer) {
  if (!canEdit.value) return
  localAnswer.value = answer
  stageCurrentAnswer()
  clearTimeout(inputTimer)
  inputTimer = setTimeout(() => {
    if (canEdit.value) saveCurrentAnswer()
  }, 500)
}

// 切换题目
async function switchQuestion(index) {
  if (index === currentIndex.value || switching || !canEdit.value) return
  switching = true
  try {
    if (!await saveCurrentAnswer() || !canEdit.value) return
    currentIndex.value = index
    loadAnswerForCurrent()
  } finally {
    switching = false
  }
}

async function prevQuestion() {
  if (currentIndex.value > 0) await switchQuestion(currentIndex.value - 1)
}

async function nextQuestion() {
  if (currentIndex.value < questions.value.length - 1) await switchQuestion(currentIndex.value + 1)
}

onMounted(() => {
  window.addEventListener('beforeunload', preventUnsavedLeave)
  window.addEventListener('online', retryOnReconnect)
  fetchQuestions()
  autosaveTimer = setInterval(() => {
    if (expired.value) finishExam(true)
    else if (canEdit.value) saveCurrentAnswer()
  }, 8000)
  timer = setInterval(() => {
    now.value = new Date()
    if (expired.value) {
      if (!finishError.value) finishExam(true)
    }
  }, 1000)
})

onUnmounted(() => {
  disposed = true
  clearInterval(timer)
  clearInterval(autosaveTimer)
  clearTimeout(inputTimer)
  window.removeEventListener('beforeunload', preventUnsavedLeave)
  window.removeEventListener('online', retryOnReconnect)
})

function retryOnReconnect() {
  if (expired.value) finishExam(true)
  else if (canEdit.value) saveCurrentAnswer()
}

function preventUnsavedLeave(event) {
  if (finished.value || (!dirty.value && !savingAnswer.value && !finishing.value)) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (finished.value) return true
  if (finishing.value) return false
  if (!dirty.value && !savingAnswer.value) return true
  try {
    await ElMessageBox.confirm('有尚未确认保存的答案，离开可能丢失，确认离开？', '离开考试')
    return true
  } catch {
    return false
  }
})
</script>

<style scoped>
.exam-answer {
  display: flex;
  height: 100vh; /* 改为 100vh，通过 padding 防止被顶部导航遮挡，实际会被框架的导航栏覆盖？ */
  background: linear-gradient(135deg, #f0f4f8 0%, #e2e8f0 100%);
  padding: 20px;
  gap: 20px;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  box-sizing: border-box;
  overflow: hidden; /* 防止溢出产生白边 */
}

.question-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
  padding-bottom: 20px; /* 底部留出呼吸空间 */
}

.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 5px;
}

.exam-header h2 {
  overflow-wrap: anywhere;
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.question-progress {
  font-size: 14px;
  color: #64748b;
  background: rgba(255,255,255,0.7);
  padding: 4px 12px;
  border-radius: 20px;
  backdrop-filter: blur(5px);
}

.question-card {
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(10px);
  border-radius: 24px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
  border: 1px solid rgba(255,255,255,0.5);
  flex: 1;
  overflow-y: auto;
}

.question-type-badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 500;
  color: #3b82f6;
  background: #eff6ff;
  padding: 4px 12px;
  border-radius: 12px;
  margin-bottom: 16px;
}

.question-title {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.5;
  margin: 0 0 24px;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.2s ease;
}
.option-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}
.option-item.selected {
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59,130,246,0.15);
}

/* 单选/判断的圆形按钮 */
.option-radio {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.option-radio.checked {
  border-color: #3b82f6;
  background: #3b82f6;
}
.radio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: white;
}

/* 多选的方形复选框 */
.option-checkbox {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 2px solid #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
  font-size: 14px;
  color: white;
}
.option-checkbox.checked {
  background: #3b82f6;
  border-color: #3b82f6;
}
.check-icon {
  font-weight: bold;
  line-height: 1;
}

.option-content {
  font-size: 16px;
  color: #334155;
  line-height: 1.4;
}
.option-label {
  font-weight: 600;
  margin-right: 6px;
  color: #1e293b;
}

.text-answer {
  margin-top: 10px;
}
.answer-textarea {
  width: 100%;
  min-height: 180px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  font-size: 16px;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}
.answer-textarea:focus {
  border-color: #3b82f6;
  background: white;
}

.nav-buttons {
  display: flex;
  justify-content: space-between;
}
.nav-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(5px);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 16px;
  font-size: 15px;
  font-weight: 500;
  color: #334155;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.nav-btn:hover:not(:disabled) {
  background: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}
.nav-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.arrow {
  font-size: 18px;
}

.info-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timer-card {
  background: rgba(255,255,255,0.75);
  backdrop-filter: blur(15px);
  border-radius: 24px;
  padding: 24px;
  text-align: center;
  border: 1px solid rgba(255,255,255,0.5);
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
}
.timer-label {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 500;
}
.timer-value {
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 1px;
}
.save-error { color: #b42318; font-size: 13px; margin-top: 12px; }
.save-status { color: #475569; font-size: 13px; margin-top: 12px; }

.question-nav-card {
  background: rgba(255,255,255,0.75);
  backdrop-filter: blur(15px);
  border-radius: 24px;
  padding: 20px;
  border: 1px solid rgba(255,255,255,0.5);
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
  flex: 1;
}
.nav-title {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 16px;
}
.question-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.q-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.q-btn:hover {
  background: #e2e8f0;
}
.q-btn.current {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59,130,246,0.3);
}
.q-btn.answered {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1e40af;
}
.q-btn.answered.current {
  background: #3b82f6;
  color: white;
}
@media (max-width: 760px) {
  .exam-answer { flex-direction: column; height: auto; min-height: 100dvh; overflow: visible; padding: 12px; gap: 12px; }
  .info-panel { order: -1; width: 100%; gap: 12px; }
  .timer-card { padding: 12px; border-radius: 8px; }
  .timer-value { font-size: 24px; letter-spacing: 0; }
  .question-nav-card { padding: 12px; border-radius: 8px; }
  .nav-title { margin-bottom: 8px; }
  .question-panel { overflow: visible; gap: 12px; }
  .question-card { padding: 16px; border-radius: 8px; overflow: visible; }
  .exam-header { gap: 12px; flex-wrap: wrap; }
  .exam-header h2 { font-size: 20px; }
  .question-title { font-size: 18px; overflow-wrap: anywhere; }
  .nav-btn { padding: 10px 14px; border-radius: 8px; }
}
</style>
