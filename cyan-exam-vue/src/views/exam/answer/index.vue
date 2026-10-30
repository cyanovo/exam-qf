<template>
  <div class="exam-answer">
    <!-- 左侧：题目区域 -->
    <div class="question-panel">
      <div class="exam-header">
        <h2>{{ examName }}</h2>
        <div class="question-progress">{{ questions.length ? currentIndex + 1 : 0 }} / {{ questions.length }}</div>
      </div>

      <div v-if="!loaded" class="question-card question-state">
        <el-skeleton v-if="loading" :rows="5" animated />
        <el-result v-else icon="warning" title="试卷暂时无法加载" sub-title="请检查网络后重试，已保存的答案不会丢失">
          <template #extra><el-button type="primary" @click="fetchQuestions">重新加载</el-button></template>
        </el-result>
      </div>
      <div v-else-if="!currentQuestion" class="question-card question-state">
        <el-empty description="这场考试暂无题目，请联系监考老师" />
      </div>
      <div class="question-card" v-else>
        <h3 v-if="currentQuestion.section">{{ currentQuestion.section }}</h3>
        <p v-if="currentQuestion.sectionDescription" class="chapter-text">{{ currentQuestion.sectionDescription }}</p>
        <div class="question-type-badge">
          {{ typeLabel(currentQuestion.questionType) }}
        </div>
        <h3 class="question-title">{{ currentQuestion.title }}</h3>
        <p v-if="currentQuestion.questionType === 'PYTHON'" class="chapter-text python-description">{{ currentQuestion.description }}</p>
        <el-alert v-if="currentQuestion.hint" class="question-hint" :title="`提示：${currentQuestion.hint}`" type="info" show-icon :closable="false" />
        <div v-if="currentQuestion.questionType==='ONSITE'">
          <p class="chapter-text">{{ currentQuestion.description }}</p>
          <p>满分 {{ currentQuestion.maxScore }}</p>
          <PythonWorkbench :key="currentQuestion.questionId" :model-value="localAnswer"
            :exam-id="examId" :question-id="currentQuestion.id" manual
            :disabled="!canEdit || currentQuestion.codeLocked"
            @submitted="submission => practicalSubmitted(currentQuestion, submission)"
            @busy="value => submittingCode = value"
            @update:model-value="value => { localAnswer = value; onTextInput(value) }" />
        </div>

        <!-- 单选题 -->
        <div v-else-if="['SINGLE_CHOICE', 'JUDGE'].includes(currentQuestion.questionType)" class="options-list">
          <button
            v-for="opt in currentQuestion.options"
            :key="opt.label"
            type="button"
            class="option-item"
            :class="{ 'selected': localAnswer === opt.label }"
            :disabled="!canEdit || switching"
            :aria-pressed="localAnswer === opt.label"
            @click="selectSingleOption(opt.label)"
          >
            <div class="option-radio" :class="{ 'checked': localAnswer === opt.label }">
              <span v-if="localAnswer === opt.label" class="radio-dot"></span>
            </div>
            <div class="option-content">
              <span class="option-label">{{ opt.label }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </button>
        </div>

        <!-- 多选题 -->
        <div v-else-if="currentQuestion.questionType === 'MULTI_CHOICE'" class="options-list">
          <button
            v-for="opt in currentQuestion.options"
            :key="opt.label"
            type="button"
            class="option-item"
            :class="{ 'selected': multiSelected.includes(opt.label) }"
            :disabled="!canEdit || switching"
            :aria-pressed="multiSelected.includes(opt.label)"
            @click="toggleMultiOption(opt.label)"
          >
            <div class="option-checkbox" :class="{ 'checked': multiSelected.includes(opt.label) }">
              <span v-if="multiSelected.includes(opt.label)" class="check-icon">✓</span>
            </div>
            <div class="option-content">
              <span class="option-label">{{ opt.label }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </button>
        </div>

        <PythonWorkbench v-else-if="currentQuestion.questionType === 'PYTHON'"
          :key="currentQuestion.questionId" :model-value="localAnswer"
          :exam-id="examId" :question-id="currentQuestion.questionId" :disabled="!canEdit"
          :samples="currentQuestion.samples" :snapshot-hash="currentQuestion.snapshotHash" :max-score="currentQuestion.maxScore"
          :save-draft="savePythonDraft"
          @busy="value => submittingCode = value"
          @update:model-value="value => { localAnswer = value; onTextInput(value) }" />
        <!-- 填空/简答题 -->
        <div v-else class="text-answer">
          <textarea
            v-model="localAnswer"
            :disabled="!canEdit"
            class="answer-textarea"
            :placeholder="currentQuestion.questionType === 'FILL_BLANK' ? '不同的空用;隔开' : '请输入答案'"
            @input="onTextInput($event.target.value)"
            @blur="onAnswerChange"
          ></textarea>
        </div>
      </div>

      <div class="nav-buttons">
        <button class="nav-btn" :disabled="!canEdit || switching || submittingCode || currentIndex === 0" @click="prevQuestion">
          <span class="arrow">←</span> 上一题
        </button>
        <button class="nav-btn" :disabled="!canEdit || switching || submittingCode || currentIndex >= questions.length - 1" @click="nextQuestion">
          下一题 <span class="arrow">→</span>
        </button>
      </div>
    </div>

    <!-- 右侧：信息面板 -->
    <div class="info-panel">
      <div class="timer-card">
        <el-button @click="router.push(`/exam/dossier/${examId}`)">异常与材料</el-button>
        <el-popover v-if="instructions" title="考试说明" width="300" trigger="click"><template #reference><el-button link>考试说明</el-button></template><p class="chapter-text">{{ instructions }}</p></el-popover>
        <div class="timer-label">剩余时间</div>
        <div class="timer-value">{{ remainingTime }}</div>
        <el-button type="primary" :disabled="!loaded || confirming || finishing || finished || submittingCode" @click="finishExam(false)">
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

      <ExamCamera v-if="loaded && !finished && !expired" />

      <div class="question-nav-card">
        <div class="nav-title">答题卡 <span class="answered-count">{{ answeredCount }} / {{ questions.length }} 已答</span></div>
        <div class="question-grid">
          <button
            v-for="(q, idx) in questions"
            :key="q.questionId"
            class="q-btn"
            :class="{ 'current': currentIndex === idx, 'answered': isAnswered(q), 'pending': isPending(q) }"
            :disabled="!canEdit || switching || submittingCode"
            :title="`${typeLabel(q.questionType)}：${q.title}`"
            :aria-label="`第 ${idx + 1} 题，${typeLabel(q.questionType)}，${isAnswered(q) ? '已答' : '未答'}${isPending(q) ? '，待保存' : ''}`"
            :aria-current="currentIndex === idx ? 'step' : undefined"
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
import ExamCamera from './ExamCamera.vue'
const PythonWorkbench = defineAsyncComponent(() => import('./PythonWorkbench.vue'))

const route = useRoute()
const router = useRouter()
const examId = ref(route.query.examId)
const examName = ref('')
const questions = ref([])
const currentIndex = ref(0)
const endTime = ref(null)
const now = ref(new Date())
const instructions = ref('')
let clockOffset = 0
let timer = null
let autosaveTimer = null
let inputTimer = null
let switching = false
let disposed = false
const loaded = ref(false)
const loading = ref(true)
const finished = ref(false)
const finishing = ref(false)
const confirming = ref(false)
const submittingCode = ref(false)
const finishError = ref(false)
const expired = computed(() => endTime.value && now.value.getTime() >= endTime.value.getTime())
const canEdit = computed(() => loaded.value && !finished.value && !finishing.value && !expired.value)
const { stage, flush, saving: savingAnswer, error: saveError, dirty } = useAnswerSaver(
  (questionId, answer) => {
    if (finished.value || expired.value || disposed) return Promise.reject(new Error('Answer editing closed'))
    if (String(questionId).startsWith('ONSITE-')) {
      return request.put(`/exam/practical/${examId.value}/${String(questionId).slice(7)}/draft`,
        { source: answer }, { headers: { repeatSubmit: false } })
    }
    return request.post('/exam/student/submitAnswer', {
      examId: examId.value, questionId, answer
    }, { headers: { repeatSubmit: false } })
  }
)

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const localAnswer = ref('')
const multiSelected = ref([])   // 多选题临时选中的标签数组
const answeredCount = computed(() => questions.value.filter(isAnswered).length)

function isAnswered(question) {
  const answer = question.questionId === currentQuestion.value?.questionId
    ? question.questionType === 'MULTI_CHOICE' ? multiSelected.value.join(',') : localAnswer.value
    : question.userAnswer
  if (question.questionType === 'PYTHON' && answer === (question.initialCode || '') && !question.userAnswer) return false
  return Boolean(String(answer ?? '').trim())
}

function isPending(question) {
  if (question.questionId !== currentQuestion.value?.questionId) return false
  const answer = question.questionType === 'MULTI_CHOICE' ? multiSelected.value.join(',') : localAnswer.value
  return answer !== (question.userAnswer || '')
}

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
    ,ONSITE: '机器人实操编程题'
  }
  return map[type] || type
}

async function fetchQuestions() {
  if (loading.value && loaded.value) return
  loading.value = true
  try {
    const session = await request.post(`/exam/student/${examId.value}/session`, null, {
      headers: { repeatSubmit: false }
    })
    if (session.data.status !== 'IN_PROGRESS') {
      finished.value = true
      loading.value = false
      ElMessage.info('这场考试已交卷')
      router.replace('/exam/wait')
      return
    }
  } catch (e) {
    loading.value = false
    return
  }
  try {
    const res = await request.get('/exam/student/questions', { params: { examId: examId.value } })
    const data = res.data
    examName.value = data.examName || ''
    instructions.value = data.policy?.instructions || ''
    const entries = data.policy?.entries || []
    const merged = [...(data.questions || []), ...(data.onsiteTasks || []).map(t=>({
      ...t,questionId:`ONSITE-${t.id}`,questionType:'ONSITE'
    }))]
    for (const q of merged) {
      const e=entries.find(e=>e.kind===(q.questionType==='ONSITE'?'ONSITE':'QUESTION') && e.id===(q.questionType==='ONSITE'?q.id:q.questionId))
      if(e) Object.assign(q,{section:e.section,sectionDescription:e.description,hint:e.hint})
    }
    const order=q=>{const i=entries.findIndex(e=>e.kind===(q.questionType==='ONSITE'?'ONSITE':'QUESTION')&&e.id===(q.questionType==='ONSITE'?q.id:q.questionId));return i<0?9999:i}
    questions.value = merged.sort((a,b)=>order(a)-order(b))
    clockOffset=data.serverTime?new Date(data.serverTime).getTime()-Date.now():0
    now.value=new Date(Date.now()+clockOffset)
    endTime.value = data.endTime ? new Date(data.endTime) : null
    loaded.value = true
    loading.value = false
    if (questions.value.length > 0) {
      // 初始化当前题目的答案显示
      loadAnswerForCurrent()
    }
  } catch {
    loading.value = false
  }
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
    localAnswer.value = q.userAnswer !== undefined && q.userAnswer !== null && q.userAnswer !== ''
      ? q.userAnswer : q.questionType === 'PYTHON' ? q.initialCode || '' : ''
    if (q.questionType === 'JUDGE' && !q.options?.some(option => option.label === localAnswer.value)) {
      const matches = (q.options || []).filter(option => option.content === localAnswer.value)
      if (matches.length === 1) localAnswer.value = matches[0].label
    }
    multiSelected.value = []
  }
}

function practicalSubmitted(question, submission) {
  question.submissionId = submission.id
  question.submittedSource = submission.source
}

function stageCurrentAnswer(forceTemplate = false) {
  if (!currentQuestion.value || finished.value || currentQuestion.value.codeLocked) return
  if (!forceTemplate && currentQuestion.value.questionType === 'PYTHON' && !currentQuestion.value.userAnswer
      && localAnswer.value === (currentQuestion.value.initialCode || '')) return
  stage(currentQuestion.value, currentQuestion.value.questionType === 'MULTI_CHOICE'
    ? multiSelected.value.join(',') : localAnswer.value)
}

function saveCurrentAnswer() {
  clearTimeout(inputTimer)
  stageCurrentAnswer()
  return flush()
}
function savePythonDraft() {
  clearTimeout(inputTimer)
  stageCurrentAnswer(true)
  return flush()
}

async function finishExam(auto = false) {
  if (!loaded.value || finishing.value || finished.value || disposed) return
  if (!auto) {
    if (submittingCode.value) return
    const pending = questions.value.filter(q => q.questionType === 'ONSITE' && !q.codeLocked
      && (q.questionId === currentQuestion.value?.questionId ? localAnswer.value : q.userAnswer || '') !== (q.submittedSource || ''))
    if (pending.length) {
      ElMessage.warning('有实操代码尚未正式提交，请先在对应题目点击“提交代码”后交卷')
      return
    }
    if (confirming.value) return
    confirming.value = true
    try {
      const unanswered = questions.value.length - answeredCount.value
      await ElMessageBox.confirm(
        unanswered ? `还有 ${unanswered} 题未作答。交卷后无法继续修改答案，确认交卷？` : '交卷后无法继续修改答案，确认交卷？',
        '确认交卷'
      )
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
  if (index === currentIndex.value || switching || submittingCode.value || !canEdit.value) return
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
    now.value = new Date(Date.now()+clockOffset)
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
  if (finished.value || (!dirty.value && !savingAnswer.value && !finishing.value && !submittingCode.value)) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (finished.value) return true
  if (finishing.value || submittingCode.value) return false
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
.chapter-text {white-space:pre-wrap;overflow-wrap:anywhere}
.question-hint {margin-bottom:16px;color:var(--el-text-color-regular);background:var(--el-fill-color-light)}
.question-hint :deep(.el-alert__title) {overflow-wrap:anywhere;white-space:pre-wrap}
.exam-answer {
  display: flex;
  height: 100vh; /* 改为 100vh，通过 padding 防止被顶部导航遮挡，实际会被框架的导航栏覆盖？ */
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
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
  color: var(--el-text-color-primary);
  margin: 0;
}

.question-progress {
  font-size: 14px;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  padding: 4px 12px;
  border-radius: 20px;
  backdrop-filter: blur(5px);
}

.question-card {
  background: var(--el-bg-color);
  backdrop-filter: blur(10px);
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
  border: 1px solid var(--el-border-color-light);
  flex: 1;
  overflow-y: auto;
}
.question-state { display: flex; align-items: center; justify-content: center; }
.question-state :deep(.el-skeleton) { width: 100%; }

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
  color: var(--el-text-color-primary);
  line-height: 1.5;
  margin: 0 0 24px;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  width: 100%;
  text-align: left;
  font: inherit;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-radius: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: all 0.2s ease;
}
.option-item:disabled { cursor: not-allowed; opacity: 0.7; }
.option-item:focus-visible, .q-btn:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.option-item:hover {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
}
.option-item.selected {
  background: var(--el-fill-color-dark);
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
  color: var(--el-text-color-regular);
  line-height: 1.4;
}
.option-label {
  font-weight: 600;
  margin-right: 6px;
  color: var(--el-text-color-primary);
}

.text-answer {
  margin-top: 10px;
}
.answer-textarea {
  width: 100%;
  min-height: 180px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 16px;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}
.answer-textarea:focus {
  border-color: #3b82f6;
  background: var(--el-bg-color);
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
  background: var(--el-bg-color);
  backdrop-filter: blur(5px);
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.nav-btn:hover:not(:disabled) {
  background: var(--el-fill-color-light);
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
  overflow-y: auto;
}

.timer-card {
  background: var(--el-bg-color);
  backdrop-filter: blur(15px);
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
}
.timer-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
  font-weight: 500;
}
.timer-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: 0;
}
.save-error { color: var(--el-color-danger); font-size: 13px; margin-top: 12px; }
.save-status { color: var(--el-text-color-regular); font-size: 13px; margin-top: 12px; }

.question-nav-card {
  background: var(--el-bg-color);
  backdrop-filter: blur(15px);
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 8px 32px rgba(0,0,0,0.06);
  flex: 1;
}
.nav-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 16px;
}
.answered-count { font-weight: 400; font-size: 12px; white-space: nowrap; }
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
.q-btn.pending { border-style: dashed; border-width: 2px; }
.q-btn:disabled { cursor: not-allowed; }
@media (max-width: 760px) {
  .exam-answer { flex-direction: column; height: auto; min-height: 100dvh; overflow: visible; padding: 12px; gap: 12px; }
  .info-panel { order: -1; width: 100%; gap: 12px; overflow: visible; }
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
