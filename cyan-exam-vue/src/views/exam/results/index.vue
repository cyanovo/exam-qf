<template>
  <main class="my-results" v-loading="loading">
    <header><h2>我的成绩</h2><el-button @click="$router.push('/exam/wait')">返回考试</el-button></header>
    <el-alert v-if="failed" title="成绩加载失败" type="error" :closable="false" />
    <el-empty v-if="!loading && !failed && !exams.length" description="暂无已发布成绩" />
    <el-table v-else :data="exams" @row-click="show">
      <el-table-column prop="examName" label="考试" min-width="180" />
      <el-table-column label="操作" min-width="180"><template #default="{ row }"><el-button link type="primary" @click.stop="show(row)">查看成绩</el-button><el-button link type="primary" @click.stop="$router.push(`/exam/dossier/${row.examId}`)">材料与异常</el-button></template></el-table-column>
    </el-table>
    <section v-if="selected" class="detail">
      <h3>{{ selected.examName }}</h3>
      <p>成绩版本 {{ selected.version }} · {{ selected.status === 'ABSENT' ? '缺考' : `总分 ${selected.total}` }}<span v-if="selected.rank != null"> · 名次 {{ selected.rank }}{{ selected.tied ? '（并列）' : '' }}</span></p>
      <el-table v-if="selected.scores" :data="selected.scores">
        <el-table-column label="题目"><template #default="{ row }">{{ row.title || row.questionId }}</template></el-table-column>
        <el-table-column prop="score" label="得分" />
        <el-table-column prop="maxScore" label="满分" />
      </el-table>
    </section>
  </main>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'
const exams = ref([]), selected = ref(null), loading = ref(false), failed = ref(false)
let sequence = 0
async function show(row) {
  const current = ++sequence
  selected.value = null
  loading.value = true
  try { const response = await request.get(`/exam/results/mine/${row.examId}`); if (current === sequence) selected.value = response.data }
  finally { if (current === sequence) loading.value = false }
}
onMounted(async () => {
  loading.value = true
  try { exams.value = (await request.get('/exam/results/mine')).data }
  catch { failed.value = true }
  finally { loading.value = false }
})
</script>
<style scoped>
.my-results { max-width: 1000px; margin: auto; padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
h2 { font-size: 22px; } .detail { margin-top: 30px; } h3 { font-size: 18px; }
</style>
