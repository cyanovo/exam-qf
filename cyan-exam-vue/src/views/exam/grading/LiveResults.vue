<template>
  <section v-loading="loading" class="live-results">
    <div class="summary-heading">
      <h3>当前成绩</h3><el-tag type="info">未发布 · 实时汇总</el-tag>
    </div>
    <el-alert v-if="failed" title="成绩汇总加载失败" type="error" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <template v-else>
      <div class="completion">
        <span>考生 {{ rows.length }}</span>
        <span>全部评分完成 {{ rows.filter(r => r.complete).length }}</span>
        <span>实操评分已锁定 {{ lockedTasks }} / {{ tasks.length }}</span>
      </div>
      <el-table :data="rows" row-key="userId">
        <el-table-column prop="studentName" label="姓名" min-width="120" />
        <el-table-column label="已评分合计" min-width="125">
          <template #default="{ row }"><strong>{{ row.total }} / {{ row.maxScore }}</strong></template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }"><el-tag :type="row.complete ? 'success' : 'warning'">{{ row.complete ? '评分完成' : '评分未完成' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="基础题与 Python" min-width="160">
          <template #default="{ row }">{{ row.theoryScore }} / {{ row.theoryMax }}<span v-if="row.theoryPending"> · 待完成</span></template>
        </el-table-column>
        <el-table-column label="机器人实操" min-width="150">
          <template #default="{ row }">{{ row.robotScore }} / {{ row.robotMax }}<span v-if="row.robotPending"> · 待评分 {{ row.robotPending }} 题</span></template>
        </el-table-column>
        <el-table-column prop="userId" label="考生编号" min-width="100" />
      </el-table>
    </template>
  </section>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '@/utils/request'

const props = defineProps({ examId: { type: Number, required: true } })
const loading = ref(false), failed = ref(false), students = ref([]), onsite = ref({})
const tasks = computed(() => onsite.value.tasks || [])
const lockedTasks = computed(() => tasks.value.filter(t => t.state === 'LOCKED').length)
const rows = computed(() => {
  const roster = new Map((onsite.value.roster || []).map(s => [s.userId, { userId: s.userId, studentName: s.name }]))
  students.value.forEach(s => roster.set(s.userId, s))
  return [...roster.values()].map(s => {
    const scores = tasks.value.map(t => t.scores?.find(score => score.userId === s.userId))
    const robotScore = scores.reduce((total, score) => total + (score?.state === 'SUBMITTED' ? Number(score.total) : 0), 0)
    const robotPending = scores.filter(score => score?.state !== 'SUBMITTED').length
    const robotMax = tasks.value.reduce((total, task) => total + Number(task.maxScore || 0), 0)
    const theoryScore = Number(s.score || 0), theoryMax = Number(s.maxScore || 0)
    const theoryPending = theoryMax > 0 && !s.gradingComplete
    return { ...s, theoryScore, theoryMax, theoryPending, robotScore, robotMax, robotPending,
      total: theoryScore + robotScore, maxScore: theoryMax + robotMax, complete: !theoryPending && !robotPending }
  })
})
async function load() {
  loading.value = true; failed.value = false
  try {
    const [theory, robot] = await Promise.all([
      request.get(`/exam/grading/students/${props.examId}`),
      request.get(`/exam/onsite/${props.examId}`)
    ])
    students.value = theory.data || []; onsite.value = robot.data || {}
  } catch { failed.value = true }
  finally { loading.value = false }
}
onMounted(load)
</script>
<style scoped>
.live-results { margin: 20px 0; }
.summary-heading, .completion { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }
h3 { font-size: 16px; margin: 0; }
</style>
