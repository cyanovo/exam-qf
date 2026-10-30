<template>
  <main class="dossier" v-loading="busy">
    <header><el-button :icon="Back" aria-label="返回" @click="router.back()" /><h2>{{ data.examName }} · 异常与材料</h2><el-tag v-if="data.archived">已归档</el-tag><el-button :icon="Refresh" aria-label="刷新" @click="load" /></header>
    <el-tabs>
      <el-tab-pane label="异常工单">
        <el-button v-if="!data.archived" type="primary" icon="Plus" @click="incidentDialog=true">提报异常</el-button>
        <el-table :data="data.incidents" @row-click="selected=$event" highlight-current-row>
          <el-table-column prop="id" label="编号" width="90" /><el-table-column prop="title" label="标题" min-width="180" /><el-table-column label="考生" min-width="100"><template #default="{row}">{{ userLabel(row.userId) }}</template></el-table-column>
          <el-table-column label="状态" width="130"><template #default="{row}">{{ states[row.state] }}</template></el-table-column>
          <el-table-column label="操作" min-width="240"><template #default="{row}">
            <el-button link @click.stop="selected=row">详情</el-button>
            <template v-if="!data.archived && data.canProcess">
              <el-button v-if="row.state==='OPEN'" link type="primary" @click.stop="act(row,'process','受理')">受理</el-button>
              <el-button v-if="row.state==='PROCESSING'" link type="primary" @click.stop="act(row,'resolve','提交处理结果')">提交处理结果</el-button>
              <el-button v-if="row.state==='APPROVED'" link @click.stop="act(row,'reopen','重新打开')">重新打开</el-button>
              <template v-if="row.state==='RESOLVED' && data.canApprove"><el-button link type="success" @click.stop="act(row,'approve','审批通过')">通过</el-button><el-button link type="danger" @click.stop="act(row,'reject','退回处理')">退回</el-button></template>
            </template>
          </template></el-table-column>
        </el-table>
        <section v-if="selected" class="history"><h3>#{{ selected.id }} {{ selected.title }}</h3><p class="multiline">{{ selected.description }}</p>
          <el-table :data="selected.events"><el-table-column prop="createdAt" label="时间" min-width="170" /><el-table-column prop="actorId" label="人员" width="100" /><el-table-column prop="action" label="操作" width="110" /><el-table-column prop="opinion" label="处理意见" min-width="200" /></el-table>
          <h4>关联证据</h4><div v-for="f in (data.files||[]).filter(f=>f.incidentId===selected.id)" :key="f.id"><el-button link :disabled="!!f.deleted" @click="download(f)">{{ f.name }}</el-button></div>
        </section>
      </el-tab-pane>
      <el-tab-pane label="成果与证据">
        <el-button v-if="!data.archived" type="primary" icon="Upload" @click="uploadDialog=true">上传材料</el-button>
        <el-table :data="data.files">
          <el-table-column prop="name" label="文件名" min-width="200" /><el-table-column label="考生" min-width="100"><template #default="{row}">{{ userLabel(row.userId) }}</template></el-table-column>
          <el-table-column label="任务" min-width="120"><template #default="{row}">{{ data.tasks?.find(t=>t.id===row.taskId)?.title || '场次' }}</template></el-table-column>
          <el-table-column label="用途" width="90"><template #default="{row}">{{ purposes[row.purpose] }}</template></el-table-column>
          <el-table-column prop="incidentId" label="工单" width="80" /><el-table-column prop="retainUntil" label="保留至" min-width="170" />
          <el-table-column label="操作" width="150"><template #default="{row}"><el-tag v-if="row.deleted">已清理</el-tag><template v-else><el-button link @click="download(row)">下载</el-button><el-button v-if="isAdmin && data.archived" link type="danger" @click="purge(row)">到期清理</el-button></template></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="incidentDialog" title="提报异常" width="min(600px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="考生"><el-select v-model="incident.userId"><el-option v-for="u in data.roster" :key="u.userId" :value="u.userId" :label="u.name || u.nickName || u.userName || String(u.userId)" /></el-select></el-form-item>
        <el-form-item label="任务"><el-select v-model="incident.taskId" clearable><el-option v-for="t in data.tasks" :key="t.id" :value="t.id" :label="t.title" /></el-select></el-form-item>
        <el-form-item label="异常类型"><el-select v-model="incident.category"><el-option label="基础设施" value="INFRASTRUCTURE" /><el-option label="设备" value="DEVICE" /><el-option label="评测 / 成绩" value="GRADING" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="标题"><el-input v-model="incident.title" maxlength="150" /></el-form-item>
        <el-form-item label="异常说明"><el-input v-model="incident.description" type="textarea" :rows="5" maxlength="5000" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="incidentDialog=false">取消</el-button><el-button type="primary" :loading="busy" @click="create">提交</el-button></template>
    </el-dialog>
    <el-dialog v-model="uploadDialog" title="上传材料" width="min(600px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="考生"><el-select v-model="upload.userId"><el-option v-for="u in data.roster" :key="u.userId" :value="u.userId" :label="u.name || u.nickName || u.userName || String(u.userId)" /></el-select></el-form-item>
        <el-form-item label="任务"><el-select v-model="upload.taskId" clearable><el-option v-for="t in data.tasks" :key="t.id" :value="t.id" :label="t.title" /></el-select></el-form-item>
        <el-form-item label="用途"><el-select v-model="upload.purpose"><el-option label="成果" value="OUTPUT" /><el-option label="证据" value="EVIDENCE" /><el-option v-if="data.canProcess" label="资源" value="RESOURCE" /></el-select></el-form-item>
        <el-form-item label="关联异常工单"><el-select v-model="upload.incidentId" clearable><el-option v-for="i in matchingIncidents" :key="i.id" :value="i.id" :label="`#${i.id} ${i.title}`" /></el-select></el-form-item>
        <el-form-item label="文件（PNG / JPEG / TXT / CSV / JSON / PY，最大 5 MB）"><input type="file" accept=".png,.jpg,.jpeg,.txt,.csv,.json,.py" @change="file=$event.target.files[0]" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="uploadDialog=false">取消</el-button><el-button type="primary" :disabled="!file" :loading="busy" @click="sendFile">上传</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import { saveAs } from 'file-saver'
const router=useRouter(), id=useRoute().params.examId, user=useUserStore()
const data=ref({}), busy=ref(false), selected=ref(null), incidentDialog=ref(false), uploadDialog=ref(false), file=ref(null)
const incident=ref({userId:null,taskId:null,category:'OTHER',title:'',description:''})
const upload=ref({userId:null,taskId:null,incidentId:null,purpose:'OUTPUT'})
const states={OPEN:'待受理',PROCESSING:'处理中',RESOLVED:'待复核',APPROVED:'已审批'}
const purposes={OUTPUT:'成果',EVIDENCE:'证据',RESOURCE:'资源'}
const isAdmin=computed(()=>user.permissions.includes('*:*:*'))
const matchingIncidents=computed(()=>(data.value.incidents||[]).filter(i=>i.userId===upload.value.userId && (i.taskId||null)===(upload.value.taskId||null) && i.state!=='APPROVED'))
const userLabel=id=>{const u=data.value.roster?.find(u=>u.userId===id);return u?.name || u?.nickName || id}
async function load() {
  busy.value=true
  try {
    data.value=(await request.get(`/exam/dossier/${id}`)).data
    if(selected.value) selected.value=data.value.incidents.find(i=>i.id===selected.value.id)
    if(data.value.roster.length===1) {incident.value.userId=data.value.roster[0].userId;upload.value.userId=data.value.roster[0].userId}
  } finally {busy.value=false}
}
async function create() {
  busy.value=true
  try {await request.post(`/exam/dossier/${id}/incidents`,{...incident.value,taskId:incident.value.taskId||null});incidentDialog.value=false;await load()} finally {busy.value=false}
}
async function act(row,key,label) {
  try {const {value}=await ElMessageBox.prompt('处理意见',label,{inputType:'textarea',inputValidator:v=>!!v?.trim()||'请填写处理意见'});await request.post(`/exam/dossier/${id}/incidents/${row.id}/${key}`,{revision:row.revision,opinion:value});await load()} catch(e) {if(e!=='cancel'&&e!=='close') console.error(e)}
}
async function sendFile() {
  if(!file.value || file.value.size>(data.value.limits?.bytes||5242880)) return ElMessage.error('文件过大或未选择文件')
  const body=new FormData()
  for(const [k,v] of Object.entries(upload.value)) if(v!==null&&v!=='') body.append(k,v)
  body.append('file',file.value)
  busy.value=true
  try {await request.post(`/exam/dossier/${id}/files`,body,{headers:{'Content-Type':'multipart/form-data',repeatSubmit:false}});uploadDialog.value=false;file.value=null;await load()} finally {busy.value=false}
}
async function download(row) {
  const blob=await request.get(`/exam/dossier/files/${row.id}`,{responseType:'blob'})
  if(blob.type?.includes('json')) {
    try {const error=JSON.parse(await blob.text());return ElMessage.error(error.msg||'下载失败')} catch {return ElMessage.error('下载失败')}
  }
  saveAs(blob,row.name)
}
async function purge(row) {
  try {await ElMessageBox.confirm(`到期清理将删除 ${row.name} 的文件内容，保留审计记录。`,'确认清理');await request.delete(`/exam/dossier/files/${row.id}`);await load()} catch(e) {if(e!=='cancel'&&e!=='close') console.error(e)}
}
onMounted(load)
</script>
<style scoped>
.dossier {padding:24px;max-width:1440px;margin:auto;background:var(--el-bg-color);color:var(--el-text-color-primary);min-height:100vh}header {display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:20px}
h2 {font-size:20px;overflow-wrap:anywhere;flex:1;min-width:0} .history {border-top:1px solid var(--el-border-color-light);margin-top:24px;padding-top:12px}.multiline {white-space:pre-wrap;overflow-wrap:anywhere}
:deep(.el-table) {margin-top:12px;border-radius:4px}
:deep(.el-table__empty-text) {color:var(--el-text-color-regular)}
:deep(.el-table th.el-table__cell) {color:var(--el-text-color-primary) !important;background:var(--el-fill-color-light) !important}
:deep(.el-select) {width:100%} input[type=file] {max-width:100%} @media(max-width:600px){.dossier{padding:12px}h2{font-size:17px}}
</style>
