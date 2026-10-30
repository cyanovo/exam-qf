<template>
  <div class="grading-student-container">
    <div class="page-card">
      <h3 v-if="!embedded" class="page-title">考试：{{ examName }}</h3>
      <el-button v-if="!embedded" @click="$router.push(`/exam/grading/results/${examId}`)">成绩复核与发布</el-button>
      <el-tooltip content="刷新成绩" placement="top">
        <el-button icon="Refresh" aria-label="刷新成绩" :disabled="loading" @click="fetchStudents" />
      </el-tooltip>
      <el-table :data="studentList" v-loading="loading" class="student-table">
        <el-table-column label="学生姓名" prop="studentName" />
        <el-table-column label="已答题数/总题数">
          <template #default="{ row }">
            <span class="progress-text">{{ row.answered }} / {{ row.total }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已批改题数" prop="graded" />
        <el-table-column label="待批改题数" prop="pending" />
        <el-table-column label="已评分合计">
          <template #default="{ row }">{{ row.score }} / {{ row.maxScore }}</template>
        </el-table-column>
        <el-table-column label="评分状态">
          <template #default="{ row }">{{ row.gradingComplete ? '批改完成' : '待完成' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            {{ ({ NOT_STARTED: '未开始', IN_PROGRESS: '答题中', SUBMITTED: '已交卷',
              AUTO_SUBMITTED: '到时交卷', GRADED: '已批改' })[row.sessionStatus] || row.sessionStatus }}
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button link type="primary"
              @click="selectedStudent = scope.row">批改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-drawer v-model="detailOpen" :title="`${selectedStudent?.studentName || ''} · 基础题与 Python`"
      size="min(1100px, 100vw)" destroy-on-close :close-on-click-modal="false" :before-close="beforeCloseDetail">
      <GradingDetail v-if="selectedStudent" ref="gradingDetail" :key="`${examId}-${selectedStudent.userId}`"
        :exam-id="Number(examId)" :user-id="selectedStudent.userId" @saved="fetchStudents" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import GradingDetail from './detail.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({ examId: Number, embedded: Boolean })
const route = useRoute()
const examId = props.examId || route.params.examId
const selectedStudent = ref(null)
const gradingDetail = ref(null)
const detailOpen = computed({ get: () => !!selectedStudent.value, set: value => { if (!value) selectedStudent.value = null } })
async function beforeCloseDetail(done) {
  if (gradingDetail.value?.saving) { ElMessage.warning('正在保存批改'); return }
  if (gradingDetail.value?.hasUnsavedChanges) {
    try { await ElMessageBox.confirm('本次改分尚未保存，确认关闭？', '未保存的批改', { type: 'warning' }) }
    catch { return }
  }
  done()
}
const examName = ref('')
const studentList = ref([])
const loading = ref(false)

function fetchStudents() {
  loading.value = true
  request.get(`/exam/grading/students/${examId}`).then(res => {
    studentList.value = res.data || []
    examName.value = '考试' + examId
  }).finally(() => { loading.value = false })
}

onMounted(() => fetchStudents())
</script>

<style scoped>
.grading-student-container {
  padding: 20px;
}

.page-card {
  background: var(--el-bg-color);
  padding: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #00695c;
  margin: 0 0 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(0, 137, 123, 0.1);
}

.progress-text {
  font-weight: 600;
  color: #00897b;
}

:deep(.student-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.student-table th.el-table__cell) {
  background-color: #e0f2f1 !important;
  color: #00695c !important;
  font-weight: 600;
}

:deep(.el-button--primary) {
  border-radius: 10px;
}
</style>
