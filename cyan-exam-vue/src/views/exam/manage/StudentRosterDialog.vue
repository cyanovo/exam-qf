<template>
  <el-dialog v-model="visible" title="分配学生" width="min(760px, 96vw)" append-to-body :close-on-click-modal="false">
    <div v-loading="loading">
      <el-alert v-if="failed" title="名单加载失败，请重试" type="error" :closable="false" />
      <el-input v-model="keyword" placeholder="学生姓名 / 学号 / 账号" clearable aria-label="查找学生" />
      <el-table :data="pageRows" height="360">
        <el-table-column width="52"><template #default="{row}"><el-checkbox :model-value="selected.includes(row.userId)" :aria-label="`选择学生 ${row.userName}`" :disabled="saving || loading" @change="checked=>toggle(row.userId,checked)" /></template></el-table-column>
        <el-table-column prop="nickName" label="姓名" min-width="120" />
        <el-table-column prop="userName" label="账号" min-width="140" />
        <el-table-column prop="studentNo" label="学号" min-width="120" />
      </el-table>
      <div class="roster-footer"><span>已选 {{ selected.length }} 人</span><el-pagination v-model:current-page="page" :page-size="10" :total="filtered.length" layout="prev, pager, next" /></div>
    </div>
    <template #footer><el-button v-if="failed" @click="load">重试</el-button><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || failed" @click="save">保存名单</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getStudentIds, saveStudents } from '@/api/system/info'
const props=defineProps({examId:[String,Number]})
const visible=defineModel({type:Boolean,default:false}), emit=defineEmits(['saved'])
const rows=ref([]), selected=ref([]), keyword=ref(''), page=ref(1), loading=ref(false), saving=ref(false), failed=ref(false)
const filtered=computed(()=>rows.value.filter(r=>`${r.nickName || ''} ${r.studentNo || ''} ${r.userName || ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const pageRows=computed(()=>filtered.value.slice((page.value-1)*10,page.value*10))
watch(keyword,()=>{page.value=1})
watch(visible,v=>{if(v) load()})
async function load() {
  loading.value=true; failed.value=false; rows.value=[]; selected.value=[]; keyword.value=''; page.value=1
  try {
    const [candidates,assigned]=await Promise.all([request.get(`/exam/manage/${props.examId}/students`),getStudentIds(props.examId)])
    rows.value=candidates.data; selected.value=assigned.data || []
    for(const id of selected.value) if(!rows.value.some(r=>r.userId===id)) rows.value.push({userId:id,userName:String(id),nickName:'账号已停用或不再具有学生角色'})
  } catch {failed.value=true} finally {loading.value=false}
}
function toggle(id,checked) {selected.value=checked?[...new Set([...selected.value,id])]:selected.value.filter(v=>v!==id)}
async function save() {
  saving.value=true
  try {await saveStudents(props.examId,selected.value);ElMessage.success('学生名单已保存');visible.value=false;emit('saved')}
  finally {saving.value=false}
}
</script>
<style scoped>
.roster-footer {display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-top:12px}
</style>
