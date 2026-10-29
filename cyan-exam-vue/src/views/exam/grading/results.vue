<template>
  <main class="results" v-loading="loading">
    <header><h2>{{ data.examName || '成绩复核' }}</h2><el-tag>{{ states[data.state] || data.state }}</el-tag>
      <span v-if="data.version">版本 {{ data.version }}</span>
      <el-tooltip content="刷新"><el-button icon="Refresh" aria-label="刷新" @click="load" /></el-tooltip>
    </header>
    <section class="actions">
      <el-button v-hasPermi="['exam:results:assign']" @click="assign">绑定复核员</el-button>
      <el-button v-if="['GRADING','REJECTED','REOPENED'].includes(data.state)" v-hasPermi="['exam:grading:edit']" type="primary" @click="act('request', '提交复核并锁定成绩')">提交复核</el-button>
      <template v-if="data.state === 'REVIEW'">
        <el-button v-hasPermi="['exam:results:review']" type="success" @click="act('review/approve','通过复核')">通过复核</el-button>
        <el-button v-hasPermi="['exam:results:review']" @click="act('review/reject','退回批改')">退回批改</el-button>
      </template>
      <el-button v-if="data.state === 'APPROVED'" v-hasPermi="['exam:results:review']" type="primary" @click="act('review/release','发布成绩给考生')">发布成绩</el-button>
      <el-button v-if="data.state === 'RELEASED'" v-hasPermi="['exam:grading:edit']" @click="act('correction','申请成绩更正')">申请更正</el-button>
      <template v-if="data.state === 'CORRECTION'">
        <el-button v-hasPermi="['exam:results:review']" @click="act('review/allowCorrection','批准更正并解锁批改')">批准更正</el-button>
        <el-button v-hasPermi="['exam:results:review']" @click="act('review/denyCorrection','拒绝更正申请')">拒绝更正</el-button>
      </template>
      <el-button v-if="['APPROVED','RELEASED','CORRECTION'].includes(data.state)" v-hasPermi="['exam:results:export']" icon="Download" @click="exportResults">导出明细</el-button>
    </section>
    <p>复核员：{{ (data.reviewers || []).map(r => `${r.name} (${r.userId})`).join('、') || '未绑定' }}</p>
    <template v-if="data.snapshot">
      <section class="statistics">
        <span>报名 {{ data.snapshot.statistics.registered }}</span><span>完成 {{ data.snapshot.statistics.completed }}</span>
        <span>最高 {{ data.snapshot.statistics.highest ?? '—' }}</span><span>最低 {{ data.snapshot.statistics.lowest ?? '—' }}</span>
        <span>平均 {{ data.snapshot.statistics.average?.toFixed(2) ?? '—' }}</span>
      </section>
      <el-table :data="data.snapshot.rows">
        <el-table-column prop="userId" label="考生编号" min-width="100" />
        <el-table-column prop="studentName" label="姓名" min-width="100" />
        <el-table-column prop="total" label="总分" min-width="80" />
        <el-table-column label="名次" min-width="100"><template #default="{ row }">{{ row.rank ?? '缺考' }}{{ row.tied ? '（并列）' : '' }}</template></el-table-column>
        <el-table-column label="逐题分数" min-width="230"><template #default="{ row }">{{ row.scores.map(s => `${s.questionId}: ${s.score}/${s.maxScore}`).join(' · ') }}</template></el-table-column>
      </el-table>
    </template>
    <h3>操作记录</h3>
    <el-table :data="data.events || []">
      <el-table-column prop="createdAt" label="时间" min-width="180" />
      <el-table-column prop="actorId" label="操作人" width="90" />
      <el-table-column label="状态" min-width="130"><template #default="{ row }">{{ states[row.newState] }}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="200" />
    </el-table>
  </main>
</template>
<script setup>
import { ref, onMounted, getCurrentInstance } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import request from '@/utils/request'
const id = useRoute().params.examId
const { proxy } = getCurrentInstance()
const data = ref({})
const loading = ref(false)
const states = { GRADING: '待提交复核', REVIEW: '复核中 · 已锁定', APPROVED: '复核通过 · 待发布', RELEASED: '已发布', REJECTED: '已退回', CORRECTION: '更正审批中', REOPENED: '允许更正' }
async function load() {
  loading.value = true
  try { data.value = (await request.get(`/exam/results/${id}`)).data }
  finally { loading.value = false }
}
async function act(path, title) {
  try {
    const { value } = await ElMessageBox.prompt('操作原因', title, { inputType: 'textarea', inputValidator: v => !!v?.trim() && v.trim().length <= 500 || '请输入1至500字' })
    loading.value = true
    await request.post(`/exam/results/${id}/${path}`, { reason: value })
    ElMessage.success('操作成功')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') console.error(error) }
  finally { loading.value = false }
}
async function assign() {
  try {
    const { value } = await ElMessageBox.prompt('复核员用户编号', '绑定复核员', { inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入有效用户编号' })
    await request.post(`/exam/results/${id}/reviewers`, { userId: Number(value) })
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') console.error(error) }
}
function exportResults() { proxy.download(`/exam/results/${id}/export`, {}, `exam-${id}-v${data.value.version}.csv`) }
onMounted(load)
</script>
<style scoped>
.results { padding: 24px; background: #fff; min-height: 100%; }
header, .actions, .statistics { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 20px; }
h2 { font-size: 20px; margin: 0; } h3 { font-size: 16px; margin-top: 30px; }
.statistics { padding: 16px 0; border-block: 1px solid #ddd; }
.actions .el-button { margin-left: 0; }
</style>
