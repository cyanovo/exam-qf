<template>
  <div class="grading-container">
    <div class="page-card">
      <h3 class="page-title">考试批改</h3>
      <el-table :data="examList" v-loading="loading" class="grading-table">
        <el-table-column label="考试名称" prop="examName" />
        <el-table-column label="开始时间" prop="startTime" width="180">
          <template #default="{ row }">
            <span>{{ parseTime(row.startTime, '{y}-{m}-{d} {h}:{i}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" prop="endTime" width="180">
          <template #default="{ row }">
            <span>{{ parseTime(row.endTime, '{y}-{m}-{d} {h}:{i}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="$router.push(`/exam/grading/students/${scope.row.examId}`)">批改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const examList = ref([])
const loading = ref(false)

function fetchExams() {
  loading.value = true
  request.get('/exam/grading/exams').then(res => {
    examList.value = res.data || []
    loading.value = false
  })
}

onMounted(() => fetchExams())
</script>

<style scoped>
.grading-container {
  padding: 20px;
}

.page-card {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 4px 24px rgba(0, 137, 123, 0.06);
  border: 1px solid rgba(0, 137, 123, 0.08);
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #00695c;
  margin: 0 0 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(0, 137, 123, 0.1);
}

:deep(.grading-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.grading-table th.el-table__cell) {
  background-color: #e0f2f1 !important;
  color: #00695c !important;
  font-weight: 600;
}

:deep(.el-button--primary) {
  border-radius: 10px;
}
</style>