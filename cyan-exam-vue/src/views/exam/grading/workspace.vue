<template>
  <main class="grading-workspace">
    <header class="workspace-header">
      <div><h2>{{ examName || `考试 ${examId}` }}</h2><span>评分与成绩</span></div>
      <el-tooltip content="刷新当前视图"><el-button icon="Refresh" aria-label="刷新当前视图" @click="revision++" /></el-tooltip>
    </header>
    <el-tabs :model-value="tab" @update:model-value="changeTab">
      <el-tab-pane v-if="canGrade" label="基础题与 Python" name="theory" />
      <el-tab-pane v-if="canGrade" label="机器人实操" name="onsite" />
      <el-tab-pane label="成绩与发布" name="results" />
    </el-tabs>
    <StudentList v-if="tab === 'theory'" :key="`theory-${examId}-${revision}`" embedded :exam-id="examId" />
    <Onsite v-else-if="tab === 'onsite'" :key="`onsite-${examId}-${revision}`" embedded :exam-id="examId" @loaded="setExamName" />
    <Results v-else :key="`results-${examId}-${revision}`" embedded :exam-id="examId" @loaded="setExamName" />
  </main>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import auth from '@/plugins/auth'
import StudentList from './studentList.vue'
import Onsite from '../onsite/index.vue'
import Results from './results.vue'

const route = useRoute(), router = useRouter()
const examId = computed(() => Number(route.params.examId))
const canGrade = computed(() => auth.hasPermi('exam:grading:list'))
const tab = computed(() => {
  const requested = route.query.tab || (route.path.includes('/students/') ? 'theory' : 'results')
  return canGrade.value && ['theory', 'onsite'].includes(requested) ? requested : 'results'
})
const examName = ref(''), revision = ref(0)
function setExamName(data) { examName.value = data.examName || '' }
function changeTab(value) { router.replace({ path: route.path, query: { ...route.query, tab: value } }) }
</script>
<style scoped>
.grading-workspace { padding: 24px; min-height: 100%; background: var(--el-bg-color); }
.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.workspace-header > div { min-width: 0; overflow-wrap: anywhere; }
.workspace-header h2 { font-size: 20px; margin: 0 0 6px; }
.workspace-header span { font-size: 14px; color: var(--el-text-color-secondary); }
:deep(.results), :deep(.onsite), :deep(.grading-student-container) { padding: 0; min-height: 0; }
@media (max-width: 600px) { .grading-workspace { padding: 12px; } }
</style>
