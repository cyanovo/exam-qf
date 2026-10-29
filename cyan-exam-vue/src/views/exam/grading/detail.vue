<template>
  <div class="grading-detail">
    <header class="detail-header">
      <div>
        <h2>{{ examName }} · {{ studentName }}</h2>
        <span>{{ statusLabel }}</span>
      </div>
      <strong>{{ totalScore }} / {{ totalMaxScore }} 分</strong>
    </header>
    <el-alert v-if="!canGrade" :title="resultsLocked ? '成绩已锁定，需先申请更正并通过复核' : '考生尚未交卷，暂不能批改'" type="warning" :closable="false" />
    <el-table :data="questions" v-loading="loading" row-key="questionId">
      <el-table-column label="题目" min-width="230">
        <template #default="{ row }">
          <strong>{{ typeLabel(row.type) }}</strong>
          <div class="question-title">{{ row.title }}</div>
          <div v-for="option in row.options || []" :key="option.label">
            {{ option.label }}. {{ option.content }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="参考答案" min-width="150" prop="referenceAnswer" />
      <el-table-column label="学生答案" min-width="150">
        <template #default="{ row }">
          <pre v-if="row.type === 'PYTHON'" class="code-answer">{{ row.studentAnswer || '未正式提交' }}</pre>
          <span v-else>{{ row.studentAnswer || "未作答" }}</span>
          <div v-if="row.type === 'PYTHON'">
            {{ evaluationLabel(row.evaluationState) }}
            <el-button v-if="row.evaluationState === 'FAILED' && !resultsLocked"
              v-hasPermi="['exam:grading:edit']" icon="RefreshRight" link
              :loading="retrying === row.formalSubmissionId" @click="retryEvaluation(row)">重试评测</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="得分 / 满分" width="185">
        <template #default="{ row }">
          <!-- Remount on lock changes: this InputNumber version sets aria-disabled only on mount. -->
          <span v-if="row.type === 'PYTHON'">{{ row.score ?? '待评测' }} / {{ row.maxScore }}</span>
          <el-input-number v-else-if="row.recordId" v-model="row.tempScore" :min="0"
            :key="String(!canGrade || loading || saving)"
            :max="row.maxScore" :step="1" step-strictly size="small" :disabled="!canGrade || loading || saving" />
          <span v-else>未作答 / {{ row.maxScore }}</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="actions">
      <el-input v-if="hasCorrections" v-model="reason" type="textarea" :rows="2"
        maxlength="500" show-word-limit placeholder="填写改分原因（必填）" class="reason-input" />
      <el-button type="primary" icon="Check" :loading="saving"
        :disabled="loading || !canGrade || !gradableCount || (hasCorrections && !reason.trim())"
        @click="submitScores">保存批改</el-button>
    </div>
    <section v-if="revisions.length" class="revision-history">
      <h3>批改记录</h3>
      <el-table :data="revisions" size="small">
        <el-table-column prop="questionId" label="题目编号" width="110" />
        <el-table-column label="分数变化" width="140">
          <template #default="{ row }">{{ row.previousScore ?? '未批改' }} → {{ row.newScore }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" />
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ Number(row.graderId) === 0 ? '系统自动判分' : row.graderId }}</template>
        </el-table-column>
        <el-table-column prop="changedAt" label="时间" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const examId = Number(route.params.examId)
const userId = Number(route.params.userId)
const examName = ref('')
const studentName = ref('')
const sessionStatus = ref('NOT_STARTED')
const resultsLocked = ref(false)
const questions = ref([])
const revisions = ref([])
const reason = ref('')
const loading = ref(false)
const saving = ref(false)
const retrying = ref(null)
const canGrade = computed(() => !resultsLocked.value && ['SUBMITTED', 'AUTO_SUBMITTED', 'GRADED'].includes(sessionStatus.value))
const statusLabel = computed(() => ({
  NOT_STARTED: '未开始', IN_PROGRESS: '答题中', SUBMITTED: '已交卷',
  AUTO_SUBMITTED: '已到时交卷', GRADED: '已批改'
})[sessionStatus.value] || sessionStatus.value)
const gradableCount = computed(() => questions.value.filter(q => q.type !== 'PYTHON' && q.recordId != null && q.tempScore != null).length)
const hasCorrections = computed(() => questions.value.some(q =>
  q.recordId != null && q.score != null && q.tempScore != null && q.tempScore !== q.score))
const totalScore = computed(() => questions.value.reduce((sum, q) => sum + (q.recordId ? (q.tempScore ?? 0) : 0), 0))
const totalMaxScore = computed(() => questions.value.reduce((sum, q) => sum + (q.maxScore || 0), 0))
const typeLabel = type => ({
  SINGLE_CHOICE: '单选题', MULTI_CHOICE: '多选题', JUDGE: '判断题',
  FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', PYTHON: 'Python 编程题'
})[type] || type
const evaluationLabel = state => ({
  NOT_SUBMITTED: '未正式提交', QUEUED: '排队中', RUNNING: '评测中',
  SUCCEEDED: '评测完成', FAILED: '系统评测失败'
})[state] || state

async function retryEvaluation(row) {
  try {
    await ElMessageBox.confirm('重新评测该次正式提交？代码和题目版本保持不变。', '重试评测')
  } catch { return }
  retrying.value = row.formalSubmissionId
  try {
    await request.post(`/exam/code/submission/${row.formalSubmissionId}/retry`)
    ElMessage.success('已重新加入评测队列')
    fetchDetail()
  } finally { retrying.value = null }
}

function fetchDetail() {
  loading.value = true
  request.get(`/exam/grading/detail/${examId}/${userId}`).then(({ data }) => {
    examName.value = data.examName
    studentName.value = data.studentName
    sessionStatus.value = data.sessionStatus
    resultsLocked.value = !!data.resultsLocked
    revisions.value = data.revisions || []
    reason.value = ''
    questions.value = (data.questions || []).map(q => ({
      ...q,
      tempScore: q.score ?? undefined
    }))
  }).finally(() => { loading.value = false })
}

function submitScores() {
  const scores = questions.value.filter(q => q.type !== 'PYTHON' && q.recordId != null && q.tempScore != null)
    .map(q => ({ recordId: q.recordId, score: q.tempScore }))
  saving.value = true
  request.post('/exam/grading/submitScore', { examId, userId, scores, reason: reason.value }).then(() => {
    ElMessage.success('批改已保存')
    fetchDetail()
  }).finally(() => { saving.value = false })
}

onMounted(fetchDetail)
</script>

<style scoped>
.grading-detail { padding: 20px; }
.detail-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.detail-header h2 { font-size: 20px; margin: 0 0 4px; }
.detail-header > div { min-width: 0; overflow-wrap: anywhere; }
.detail-header span { color: #61717a; }
.detail-header strong { white-space: nowrap; }
.question-title { margin: 6px 0; white-space: pre-wrap; }
.code-answer { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 240px; overflow: auto; }
.actions { display: flex; align-items: flex-end; justify-content: flex-end; gap: 12px; margin-top: 16px; }
.reason-input { max-width: 440px; }
.revision-history { margin-top: 28px; }
.revision-history h3 { font-size: 16px; }
@media (max-width: 700px) { .actions { flex-wrap: wrap; } .reason-input { max-width: 100%; } }
</style>
