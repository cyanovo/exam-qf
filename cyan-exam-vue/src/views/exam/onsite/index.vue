<template>
  <main class="onsite" v-loading="loading">
    <header v-if="!embedded">
      <div><h2>机器人实操评分</h2><span class="muted">{{ data.examName || '实操编程考核' }}</span></div>
      <div class="toolbar">
        <el-select v-model="examId" aria-label="选择考试" placeholder="选择考试" @change="load" class="exam-select">
          <el-option v-for="exam in exams" :key="exam.examId" :label="exam.examName" :value="exam.examId" />
        </el-select>
        <el-tooltip content="刷新"><el-button icon="Refresh" aria-label="刷新" @click="load" /></el-tooltip>
        <el-button v-if="data.canManage" v-hasPermi="['exam:exam:list']" type="primary" @click="$router.push(`/exam/manage/${examId}`)">考试编排</el-button>
        <el-button v-if="examId" @click="$router.push(`/exam/dossier/${examId}`)">异常与材料</el-button>
      </div>
    </header>
    <el-alert v-if="failed" title="加载失败，请重试" type="error" :closable="false" />
    <el-alert v-if="data.resultLocked" title="成绩已锁定" type="warning" :closable="false" />
    <el-empty v-if="!loading && !tasks.length && !failed" description="暂无机器人实操编程题" />
    <div v-else-if="tasks.length" class="workspace">
      <aside>
        <button v-for="task in tasks" :key="task.id" class="task-link" :class="{ active: selectedId === task.id }" @click="selectTask(task.id)">
          <strong>{{ task.title }}</strong>
          <span>{{ labels[task.state] }} · {{ task.maxScore }} 分</span>
          <span>已提交 {{ task.submittedUsers?.length || 0 }} 人</span>
        </button>
      </aside>
      <section v-if="selected" class="task-content">
        <div class="section-heading">
          <h3>{{ selected.title }}</h3>
          <el-tag :type="selected.state === 'LOCKED' ? 'success' : 'info'">{{ labels[selected.state] }}</el-tag>
          <span>{{ selected.durationMinutes }} 分钟 · 满分 {{ selected.maxScore }}</span>
        </div>
        <p class="description">{{ selected.description }}</p>
        <div v-if="data.canManage" class="toolbar">
          <el-button v-if="selected.state === 'DRAFT'" type="primary" @click="$router.push(`/exam/manage/${examId}`)">统一发布</el-button>
          <el-tooltip content="全部考生提交评分后才能锁定"><span v-if="selected.state === 'OPEN'"><el-button icon="Lock" :disabled="busy || data.resultLocked || !allScored" @click="taskAction('lock','锁定评分')">锁定评分</el-button></span></el-tooltip>
          <el-button v-if="selected.state === 'LOCKED'" icon="Unlock" :disabled="busy || data.resultLocked" @click="taskAction('reopen','解锁任务')">解锁任务</el-button>
          <el-button v-if="!embedded" v-hasPermi="['exam:grading:list','exam:results:review']" @click="$router.push(`/exam/grading/workspace/${examId}`)">评分与成绩</el-button>
        </div>
        <el-tabs v-model="tab">
          <el-tab-pane label="考生评分" name="scores">
            <el-table :data="roster" highlight-current-row @row-click="openScore">
              <el-table-column prop="userId" label="考生编号" width="100" />
              <el-table-column prop="name" label="姓名" min-width="120" />
              <el-table-column label="状态" min-width="100"><template #default="{ row }"><el-tag :type="row.score?.state === 'SUBMITTED' ? 'success' : 'info'">{{ labels[row.score?.state] || '未评分' }}</el-tag></template></el-table-column>
              <el-table-column label="分数" width="100"><template #default="{ row }">{{ row.score?.total ?? '-' }}</template></el-table-column>
              <el-table-column label="评分评委" min-width="110"><template #default="{ row }">{{ row.score?.judgeName || row.score?.judgeId || '-' }}</template></el-table-column>
              <el-table-column label="操作" min-width="180"><template #default="{ row }">
                <el-button link type="primary" @click.stop="openScore(row)">{{ selected.canScore && selected.state === 'OPEN' && !data.resultLocked && (!row.score || row.score.canEdit) ? '评分' : '查看' }}</el-button>
                <el-button v-if="row.score?.canWithdraw && selected.state === 'OPEN'" link :disabled="busy || data.resultLocked" @click.stop="withdraw(row,'request','申请撤回')">申请撤回</el-button>
                <template v-if="row.score?.canApprove && selected.state === 'OPEN'">
                  <el-button link type="success" :disabled="busy || data.resultLocked" @click.stop="withdraw(row,'approve','批准撤回')">批准</el-button>
                  <el-button link type="danger" :disabled="busy || data.resultLocked" @click.stop="withdraw(row,'reject','驳回撤回')">驳回</el-button>
                </template>
              </template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="评分表" name="rubric">
            <el-table :data="selected.items"><el-table-column prop="title" label="评分项" /><el-table-column prop="maxScore" label="满分" width="100" /></el-table>
          </el-tab-pane>
          <el-tab-pane label="操作记录" name="events">
            <el-table :data="selected.events">
              <el-table-column prop="createdAt" label="时间" min-width="180" />
              <el-table-column prop="actorId" label="操作人" width="90" />
              <el-table-column prop="userId" label="考生" width="90" />
              <el-table-column label="操作" min-width="120"><template #default="{ row }">{{ actions[row.action] || row.action }}</template></el-table-column>
              <el-table-column prop="reason" label="备注 / 原因" min-width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
    <el-dialog v-model="scoring" :title="`${student?.name || ''} · 现场评分`" width="620px" class="onsite-dialog" :close-on-click-modal="false">
      <template v-if="selected && student">
        <div v-loading="codeLoading">
          <el-alert v-if="codeError" title="代码加载失败，请关闭后重试" type="error" :closable="false" />
          <el-empty v-else-if="!codeLoading && !codeHistory.length" description="考生尚未提交代码" />
          <template v-if="codeHistory.length">
            <el-select v-model="viewSubmissionId" aria-label="代码版本" class="code-version">
              <el-option v-for="submission in codeHistory" :key="submission.id" :value="submission.id"
                :label="`提交 ${submission.id} · ${submission.receivedAt}`" />
            </el-select>
            <p>评分关联提交：{{ scoreForm.submissionId || '历史评分未关联代码' }}</p>
            <pre class="submitted-code">{{ viewedSubmission?.source }}</pre>
          </template>
        </div>
        <el-form v-if="!codeLoading && !codeError" :key="editable ? 'editable' : 'readonly'" label-position="top">
          <el-alert v-if="scoreBlockReason" :title="scoreBlockReason" type="warning" :closable="false" show-icon class="score-guidance" />
          <el-form-item v-for="(item,i) in selected.items" :key="i" :label="`${item.title}（满分 ${item.maxScore}）`">
            <el-input-number v-model="scoreForm.points[i]" :min="0" :max="item.maxScore" :precision="0" :disabled="!editable || busy" :aria-label="item.title" />
          </el-form-item>
          <el-form-item label="评分备注"><el-input v-model="scoreForm.note" type="textarea" :rows="3" maxlength="1000" :disabled="!editable || busy" /></el-form-item>
        </el-form>
        <div class="score-total">当前合计 <strong>{{ scoreTotal }}</strong> / {{ selected.maxScore }}</div>
      </template>
      <template #footer>
        <el-button @click="scoring=false">关闭</el-button>
        <el-button v-if="canMarkBlank" type="primary" :loading="busy" @click="markBlank">未提交，记0分</el-button>
        <template v-if="editable"><el-button :loading="busy" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="busy" @click="save(true)">提交评分</el-button></template>
      </template>
    </el-dialog>
  </main>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const props = defineProps({ examId: Number, embedded: Boolean })
const emit = defineEmits(['loaded'])
const exams = ref([]), examId = ref(props.examId || null), data = ref({}), selectedId = ref(null)
const loading = ref(false), busy = ref(false), failed = ref(false), scoring = ref(false)
const scoreForm = ref({ points: [] }), student = ref(null), tab = ref('scores')
const codeHistory = ref([]), codeLoading = ref(false), codeError = ref(false), viewSubmissionId = ref(null)
const viewedSubmission = computed(() => codeHistory.value.find(s => s.id === viewSubmissionId.value))
let codeSequence = 0
const tasks = computed(() => data.value.tasks || [])
const selected = computed(() => tasks.value.find(t => t.id === selectedId.value))
const roster = computed(() => (data.value.roster || [])
  .map(u => ({ ...u, score: selected.value?.scores.find(s => s.userId === u.userId) }))
  .filter(u => data.value.canManage || data.value.examEnded || u.score || selected.value?.submittedUsers?.includes(u.userId)))
const editable = computed(() => !codeLoading.value && !codeError.value && !!scoreForm.value.submissionId
  && viewSubmissionId.value === scoreForm.value.submissionId
  && selected.value?.canScore && selected.value.state === 'OPEN' && !data.value.resultLocked && (!student.value?.score || student.value.score.canEdit))
const canMarkBlank = computed(() => !codeLoading.value && !codeError.value && !codeHistory.value.length
  && data.value.examEnded && selected.value?.canScore && selected.value.state === 'OPEN'
  && !data.value.resultLocked && (!student.value?.score || student.value.score.canEdit))
const scoreBlockReason = computed(() => {
  if (data.value.resultLocked) return '成绩已锁定或考试已归档，只能查看。修改成绩需先完成更正审批。'
  if (!selected.value?.canScore) return '当前账号没有评委评分权限。'
  if (selected.value?.state !== 'OPEN') return selected.value?.state === 'LOCKED'
    ? '本题评分已锁定，请由考试管理员解锁任务后再评分。' : '本题尚未发布，暂不能评分。'
  if (student.value?.score?.state === 'WITHDRAWAL') return '评分撤回申请正在审批，通过后才能修改。'
  if (student.value?.score?.state === 'SUBMITTED') return '评分已提交。需要修改时，请关闭窗口，在考生行申请撤回。'
  if (student.value?.score && !student.value.score.canEdit) return '其他评委已开始评分，当前只能查看。'
  if (!scoreForm.value.submissionId) return data.value.examEnded
    ? '该考生没有正式提交实操代码，可点击“未提交，记0分”完成白卷评分，不会生成或补交代码。'
    : '该考生没有正式提交实操代码。考试结束后可按白卷记0分，考试进行中请等待考生提交。'
  if (viewSubmissionId.value !== scoreForm.value.submissionId) return '正在查看历史代码。请选择评分关联的提交版本后再评分。'
  return ''
})
const scoreTotal = computed(() => scoreForm.value.points.reduce((n,p) => n + (p || 0), 0))
const allScored = computed(()=>roster.value.length>0 && roster.value.every(r=>r.score?.state==='SUBMITTED'))
const labels = { DRAFT: '草稿', OPEN: '评分开放', LOCKED: '已锁定', SUBMITTED: '已提交', WITHDRAWAL: '撤回待审批' }
const actions = { CREATE: '创建任务', PUBLISH: '发布任务', LOCK: '锁定评分', REOPEN: '解锁任务', SAVE: '保存草稿', SUBMIT: '提交评分', WITHDRAW_REQUEST: '申请撤回', WITHDRAW_APPROVE: '批准撤回', WITHDRAW_REJECT: '驳回撤回' }
let sequence = 0
async function load() {
  if (!examId.value) return
  const current = ++sequence
  loading.value = true; failed.value = false; scoring.value = false
  try {
    const response = await request.get(`/exam/onsite/${examId.value}`)
    if (current !== sequence) return
    data.value = response.data
    emit('loaded', data.value)
    if (!tasks.value.some(t => t.id === selectedId.value)) selectedId.value = tasks.value[0]?.id
  } catch { if (current === sequence) { failed.value = true; data.value = {} } }
  finally { if (current === sequence) loading.value = false }
}
function selectTask(id) { selectedId.value = id; tab.value = 'scores' }
async function action(path, title) {
  if (busy.value) return
  try {
    const { value } = await ElMessageBox.prompt('操作原因', title, { inputType: 'textarea', inputValidator: v => !!v?.trim() && v.trim().length <= 1000 || '请输入1至1000字' })
    busy.value = true
    await request.post(path, { reason: value })
    await load(); ElMessage.success('操作成功')
  } catch {} finally { busy.value = false }
}
function taskAction(name, title) { return action(`/exam/onsite/${examId.value}/tasks/${selectedId.value}/${name}`, title) }
function withdraw(row, name, title) { return action(`/exam/onsite/${examId.value}/tasks/${selectedId.value}/scores/${row.userId}/${name}`, title) }
async function openScore(row) {
  const current = ++codeSequence
  student.value = row
  scoreForm.value = { points: row.score ? JSON.parse(row.score.points) : selected.value.items.map(() => null), note: row.score?.note || '', revision: row.score?.revision || 0 }
  codeHistory.value = []; codeError.value = false; codeLoading.value = true
  scoring.value = true
  try {
    const response = await request.get(`/exam/practical/${examId.value}/${selectedId.value}/history`, { params: { userId: row.userId } })
    if (current !== codeSequence) return
    codeHistory.value = response.data
    scoreForm.value.submissionId = row.score?.submissionId || (row.score?.state && row.score.state !== 'DRAFT' ? null : response.data[0]?.id)
    viewSubmissionId.value = scoreForm.value.submissionId || response.data[0]?.id
  } catch { if (current === codeSequence) codeError.value = true }
  finally { if (current === codeSequence) codeLoading.value = false }
}
async function save(submit) {
  if (!editable.value || busy.value) return
  if (submit && scoreForm.value.points.some(p => !Number.isInteger(p))) { ElMessage.warning('请完成全部评分项'); return }
  if (submit) { try { await ElMessageBox.confirm('提交后评分将只读，修改需申请撤回。', '提交评分', { type: 'warning' }) } catch { return } }
  busy.value = true
  try {
    await request.put(`/exam/onsite/${examId.value}/tasks/${selectedId.value}/scores/${student.value.userId}`, { ...scoreForm.value, points: scoreForm.value.points.map(p => p ?? null), submit })
    scoring.value = false; await load(); ElMessage.success(submit ? '评分已提交' : '草稿已保存')
  } catch (error) { if (error !== 'cancel' && error !== 'close') await load() } finally { busy.value = false }
}
async function markBlank() {
  if (!canMarkBlank.value || busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm('确认该考生本题未正式提交，所有步骤记0分？此操作会留下评分记录，不会替学生提交代码。', '白卷记零', { type: 'warning' })
    await request.put(`/exam/onsite/${examId.value}/tasks/${selectedId.value}/scores/${student.value.userId}`, {
      points: selected.value.items.map(() => 0), revision: scoreForm.value.revision,
      submit: true, submissionId: null, markBlank: true
    })
    scoring.value = false
    await load()
    ElMessage.success('已按白卷记0分')
  } catch (error) { if (error !== 'cancel' && error !== 'close') await load() } finally { busy.value = false }
}
onMounted(async () => {
  loading.value = true
  try {
    if (props.embedded) { await load(); return }
    exams.value = (await request.get('/exam/onsite/exams')).data
    examId.value = exams.value.find(e => e.examId === Number(route.query.examId))?.examId || exams.value[0]?.examId
    await load()
  } catch { failed.value = true } finally { loading.value = false }
})
</script>
<style scoped>
.onsite { padding: 24px; background: var(--el-bg-color); min-height: 100%; color: var(--el-text-color-primary); letter-spacing: 0; }
.submitted-code { max-height: 320px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; background: var(--el-fill-color-light); color: var(--el-text-color-primary); padding: 12px; }
.code-version { width: 100%; }
.score-guidance { margin: 12px 0; }
header, .toolbar, .section-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
header { justify-content: space-between; margin-bottom: 24px; }
h2 { font-size: 20px; margin: 0 0 6px; } h3 { font-size: 18px; margin: 0; } h4 { font-size: 15px; }
.muted, .section-heading > span { color: #66716b; font-size: 13px; }
.exam-select { width: 250px; max-width: 100%; }
.workspace { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 24px; border-top: 1px solid #e0e5e2; padding-top: 20px; }
aside { border-right: 1px solid #e0e5e2; padding-right: 16px; }
.task-link { display: flex; flex-direction: column; gap: 8px; width: 100%; text-align: left; background: transparent; border: 0; border-left: 3px solid transparent; padding: 14px 12px; cursor: pointer; color: inherit; overflow-wrap: anywhere; }
.task-link.active { border-left-color: #008577; background: var(--el-fill-color-light); }
.task-link span { color: #66716b; font-size: 12px; }
.task-content { min-width: 0; }
.description { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; font-size: 14px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.rubric-row { display: grid; grid-template-columns: minmax(0,1fr) 150px 34px; gap: 8px; margin-bottom: 12px; }
.rubric-row .el-input-number { width: 100%; }
.score-total { border-top: 1px solid #e0e5e2; padding-top: 16px; }
.score-total strong { color: #008577; font-size: 24px; margin-left: 10px; }
.toolbar .el-button { margin-left: 0; }
@media (max-width: 760px) {
  .onsite { padding: 16px; }
  .workspace { grid-template-columns: minmax(0,1fr); gap: 16px; }
  aside { border-right: 0; border-bottom: 1px solid #e0e5e2; padding: 0 0 12px; }
  .form-row { grid-template-columns: minmax(0,1fr); gap: 0; }
  .rubric-row { grid-template-columns: minmax(0,1fr) 110px 34px; }
}
</style>
<style>
.onsite-dialog { max-width: calc(100vw - 24px); border-radius: 6px; }
</style>
