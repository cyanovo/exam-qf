<template>
  <main class="review-list" v-loading="loading">
    <h2>成绩复核</h2>
    <el-table :data="exams">
      <el-table-column prop="examId" label="考试编号" width="110" />
      <el-table-column prop="examName" label="考试名称" min-width="200" />
      <el-table-column label="操作" width="130"><template #default="{ row }">
        <el-button link type="primary" @click="$router.push(`/exam/grading/results/${row.examId}`)">查看成绩</el-button>
      </template></el-table-column>
    </el-table>
  </main>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
const exams = ref([]), loading = ref(false)
onMounted(async () => {
  loading.value = true
  try { exams.value = (await request.get('/exam/results/review-exams')).data }
  finally { loading.value = false }
})
</script>
<style scoped>
.review-list { padding: 24px; background: white; } h2 { font-size: 20px; }
</style>
