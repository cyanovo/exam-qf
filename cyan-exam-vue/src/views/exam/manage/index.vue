<template>
  <main class="app-container" v-loading="busy">
    <header class="toolbar">
      <h2>{{ data.exam?.examName || '考试编排' }}</h2>
      <el-tag>{{ states[data.state] || data.state }}</el-tag>
      <el-button @click="router.push(`/exam/dossier/${id}`)">异常与材料</el-button>
      <el-button v-if="data.exam?.publishedAt" v-hasPermi="['exam:grading:list']" @click="router.push(`/exam/grading/students/${id}`)">基础题批改</el-button>
      <el-button @click="router.push(`/exam/onsite?examId=${id}`)">实操评分</el-button>
      <el-button v-if="editable" v-hasPermi="['exam:exam:edit']" @click="studentOpen=true">分配学生</el-button>
      <el-button v-if="editable" type="primary" @click="save">保存配置</el-button>
      <el-button v-if="editable" type="success" @click="publish">统一发布</el-button>
      <el-button v-if="['PUBLISHED','READY'].includes(data.state)" @click="action('withdraw','撤回')">撤回</el-button>
      <el-button v-if="data.state==='RUNNING'" type="danger" @click="action('terminate','终止')">终止</el-button>
      <el-button v-if="['CLOSED','TERMINATED'].includes(data.state)" @click="action('archive','归档')">归档</el-button>
    </header>
    <el-tabs>
      <el-tab-pane label="场次与成绩策略">
        <el-form label-position="top" :disabled="!editable">
          <div class="policy-grid">
            <el-form-item label="独立作答时长（分钟，0 为场次结束）"><el-input-number v-model="form.durationMinutes" :min="0" :max="1440" /></el-form-item>
            <el-form-item label="提前入场窗口（分钟）"><el-input-number v-model="form.earlyEntryMinutes" :min="0" :max="1440" /></el-form-item>
            <el-form-item label="材料保留天数"><el-input-number v-model="form.policy.retentionDays" :min="30" :max="3650" /></el-form-item>
            <el-form-item label="同分规则">
              <el-select v-model="form.policy.tieRule">
                <el-option label="同分并列" value="SHARED" /><el-option label="指定实操编程题得分优先" value="TASK_SCORE" /><el-option label="较早交卷优先" value="SUBMITTED_AT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.policy.tieRule==='TASK_SCORE'" label="同分比较任务"><el-select v-model="form.policy.tieTaskId"><el-option v-for="t in data.tasks" :key="t.id" :label="t.title" :value="t.id" /></el-select></el-form-item>
            <el-form-item label="向考生公开的成绩">
              <el-select v-model="form.policy.releasePolicy"><el-option label="总分、名次与各项成绩" value="DETAIL" /><el-option label="总分与名次" value="TOTAL_RANK" /><el-option label="仅总分" value="TOTAL" /></el-select>
            </el-form-item>
          </div>
          <el-form-item label="考试说明"><el-input v-model="form.policy.instructions" type="textarea" :rows="4" maxlength="10000" show-word-limit /></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="章节与题目顺序">
        <div class="toolbar"><el-button :disabled="!editable" icon="Refresh" @click="syncEntries">补入未编排题目</el-button><el-button :disabled="!editable" icon="Plus" @click="openTaskDialog">新增机器人实操编程题</el-button></div>
        <div class="table-scroll"><el-table :data="form.policy.entries" :row-key="row => `${row.kind}:${row.id}`" min-width="700">
          <el-table-column label="顺序" width="100"><template #default="{ $index }">
            <el-tooltip content="上移"><el-button :icon="ArrowUp" circle size="small" :disabled="!editable || $index===0" aria-label="上移" @click="move($index,-1)" /></el-tooltip>
            <el-tooltip content="下移"><el-button :icon="ArrowDown" circle size="small" :disabled="!editable || $index===form.policy.entries.length-1" aria-label="下移" @click="move($index,1)" /></el-tooltip>
          </template></el-table-column>
          <el-table-column label="题目" min-width="180"><template #default="{row}">{{ title(row) }}<div><el-tag size="small">{{ row.kind==='ONSITE'?'机器人实操编程':'基础题' }}</el-tag></div></template></el-table-column>
          <el-table-column label="章节" min-width="150"><template #default="{row}"><el-input v-model="row.section" :disabled="!editable" maxlength="100" /></template></el-table-column>
          <el-table-column label="章节说明" min-width="220"><template #default="{row}"><el-input v-model="row.description" type="textarea" :disabled="!editable" maxlength="3000" /></template></el-table-column>
          <el-table-column label="作答提示" min-width="220"><template #default="{row}"><el-input v-model="row.hint" type="textarea" :disabled="!editable" maxlength="1000" /></template></el-table-column>
        </el-table></div>
      </el-tab-pane>
      <el-tab-pane label="操作历史"><el-table :data="data.history"><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column prop="actorId" label="操作人" /><el-table-column prop="action" label="操作" /><el-table-column prop="reason" label="原因" min-width="220" /></el-table></el-tab-pane>
    </el-tabs>
    <StudentRosterDialog v-model="studentOpen" :exam-id="id" />
    <el-dialog v-model="taskDialog" title="新增机器人实操编程题" width="min(640px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="来源">
          <el-segmented v-model="taskSource" :options="[{label:'从题库选择',value:'bank'},{label:'现场新建',value:'new'}]" />
        </el-form-item>
        <el-form-item v-if="taskSource==='bank'" label="机器人实操编程题">
          <el-select v-model="bankQuestionId" filterable placeholder="选择已启用的实操题">
            <el-option v-for="q in robotQuestions" :key="q.questionId" :value="q.questionId" :label="q.title" />
          </el-select>
        </el-form-item>
        <template v-else>
          <el-form-item label="题目名称"><el-input v-model="task.title" maxlength="120" /></el-form-item>
          <el-form-item label="题干与机器人运行要求"><el-input v-model="task.description" type="textarea" maxlength="5000" /></el-form-item>
        </template>
        <el-form-item label="机器人运行观察时长（分钟）"><el-input-number v-model="task.durationMinutes" :min="1" :max="240" /></el-form-item>
        <div v-for="(item,index) in (taskSource==='new' ? task.items : [])" :key="index" class="rubric">
          <el-input v-model="item.title" placeholder="运行步骤与得分条件" maxlength="120" /><el-input-number v-model="item.maxScore" :min="1" :max="100" /><el-button :icon="Delete" :disabled="task.items.length===1" aria-label="删除评分项" @click="task.items.splice(index,1)" />
        </div>
        <el-button v-if="taskSource==='new'" icon="Plus" :disabled="task.items.length>=30" @click="task.items.push({title:'',maxScore:10})">添加评分项</el-button>
      </el-form>
      <template #footer><el-button @click="taskDialog=false">取消</el-button><el-button type="primary" :loading="busy" @click="createTask">创建</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUp, ArrowDown, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import StudentRosterDialog from './StudentRosterDialog.vue'
const studentOpen=ref(false)
const route=useRoute(), router=useRouter(), id=route.params.examId
const busy=ref(false), data=ref({}), taskDialog=ref(false)
const taskSource=ref('bank'), bankQuestionId=ref(null), robotQuestions=ref([])
const form=ref({durationMinutes:0,earlyEntryMinutes:30,policy:{instructions:'',tieRule:'SHARED',releasePolicy:'DETAIL',retentionDays:365,entries:[]}})
const task=ref({title:'',description:'',durationMinutes:10,items:[{title:'',maxScore:10}]})
const states={DRAFT:'未发布',PUBLISHED:'已发布',READY:'等待入场',RUNNING:'进行中',CLOSED:'已结束',TERMINATED:'已终止',ARCHIVED:'已归档'}
const editable=computed(()=>data.value.state==='DRAFT' && new Date(data.value.exam?.startTime?.replace(' ','T')).getTime()>Date.now())
async function load() {
  busy.value=true
  try {
    data.value=(await request.get(`/exam/manage/${id}`)).data
    form.value={durationMinutes:data.value.exam.durationMinutes,earlyEntryMinutes:data.value.exam.earlyEntryMinutes,policy:JSON.parse(JSON.stringify(data.value.policy))}
    if(!form.value.policy.entries.length) syncEntries()
  } finally {busy.value=false}
}
function syncEntries() {
  const all=[...(data.value.questions||[]).map(q=>({kind:'QUESTION',id:q.questionId})),...(data.value.tasks||[]).map(t=>({kind:'ONSITE',id:t.id}))]
  form.value.policy.entries=form.value.policy.entries.filter(e=>all.some(a=>a.kind===e.kind&&a.id===e.id))
  for(const e of all) if(!form.value.policy.entries.some(a=>a.kind===e.kind&&a.id===e.id)) form.value.policy.entries.push({...e,section:'',description:'',hint:''})
}
function title(e) { return e.kind==='QUESTION' ? data.value.questions?.find(q=>q.questionId===e.id)?.title : data.value.tasks?.find(t=>t.id===e.id)?.title }
function move(i,d) { const es=form.value.policy.entries; [es[i],es[i+d]]=[es[i+d],es[i]] }
async function openTaskDialog() {
  robotQuestions.value=(await request.get(`/exam/manage/${id}/robot-questions`)).data || []
  bankQuestionId.value=null
  taskSource.value=robotQuestions.value.length ? 'bank' : 'new'
  taskDialog.value=true
}
async function save() { await request.put(`/exam/manage/${id}`,form.value); ElMessage.success('配置已保存') }
async function publish() {
  try { await ElMessageBox.confirm('发布后全部题目、章节与策略将锁定。确认发布？','统一发布'); await save(); await request.post(`/exam/exam/${id}/publish`); await load() } catch(e) { if(e!=='cancel'&&e!=='close') console.error(e) }
}
async function action(key,label) {
  try { const {value}=await ElMessageBox.prompt(`请填写${label}原因`,label,{inputValidator:v=>!!v?.trim()||'原因不能为空'}); await request.post(`/exam/manage/${id}/${key}`,{reason:value}); await load() } catch(e) { if(e!=='cancel'&&e!=='close') console.error(e) }
}
async function createTask() {
  if (taskSource.value==='bank' && !bankQuestionId.value) { ElMessage.warning('请选择实操题'); return }
  busy.value=true
  try {
    if (taskSource.value==='bank')
      await request.post(`/exam/onsite/${id}/tasks/from-bank`,{questionId:bankQuestionId.value,durationMinutes:task.value.durationMinutes})
    else await request.post(`/exam/onsite/${id}/tasks`,task.value)
    data.value=(await request.get(`/exam/manage/${id}`)).data
    syncEntries(); taskDialog.value=false
    task.value={title:'',description:'',durationMinutes:10,items:[{title:'',maxScore:10}]}
  } finally {busy.value=false}
}
onMounted(load)
</script>
<style scoped>
h2 {font-size:20px;margin:0} .toolbar {display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:20px}
.policy-grid {display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:0 24px}
.table-scroll {overflow:auto}.rubric {display:flex;gap:8px;margin-bottom:12px}.rubric .el-input {min-width:100px}
:deep(.el-select) {width:100%} :deep(.el-button+.el-button) {margin-left:0}
</style>
