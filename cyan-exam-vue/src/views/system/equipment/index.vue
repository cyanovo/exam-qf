<template>
  <div class="app-container equipment-page">
    <header><h2>设备与工位</h2><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></header>
    <el-tabs v-model="kind" @tab-change="page = 1">
      <el-tab-pane label="设备台账" name="devices" />
      <el-tab-pane label="考场工位" name="stations" />
    </el-tabs>
    <div class="toolbar">
      <el-input v-model="search" clearable placeholder="编号、位置、型号或联系人" :prefix-icon="Search" @input="page = 1" />
      <el-select v-if="kind === 'devices'" v-model="state" clearable placeholder="全部资产状态" @change="page = 1">
        <el-option v-for="(label, value) in states" :key="value" :value="value" :label="label" />
      </el-select>
      <el-button v-hasPermi="['system:equipment:edit']" type="primary" :icon="Plus" @click="edit()">新增{{ kind === 'devices' ? '设备' : '工位' }}</el-button>
      <el-button :icon="Download" :disabled="loading || failed" @click="exportRows">导出</el-button>
    </div>
    <el-alert v-if="failed" title="台账加载失败，请刷新重试" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows.slice((page-1)*20,page*20)" border stripe>
      <el-table-column prop="code" label="编号" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="kind === 'devices'" prop="model" label="型号" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="kind === 'devices'" label="资产状态" width="120">
        <template #default="{row}"><el-tag :type="row.assetState === 'AVAILABLE' ? 'success' : row.assetState === 'RETIRED' ? 'danger' : 'warning'">{{ states[row.assetState] }}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="kind === 'devices'" label="分配工位" min-width="130"><template #default="{row}">{{ row.stationCode || '未分配' }}</template></el-table-column>
      <el-table-column v-else prop="location" label="位置" min-width="180" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系方式" min-width="150" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{row}">
          <el-tooltip content="编辑"><el-button v-hasPermi="['system:equipment:edit']" :icon="Edit" aria-label="编辑" link type="primary" @click="edit(row)" /></el-tooltip>
          <el-tooltip content="删除"><el-button v-hasPermi="['system:equipment:edit']" :icon="Delete" aria-label="删除" link type="danger" @click="remove(row)" /></el-tooltip>
        </template>
      </el-table-column>
      <template #empty>暂无{{ kind === 'devices' ? '设备' : '工位' }}</template>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="rows.length" layout="total, prev, pager, next" />
    <el-dialog v-model="opened" :title="`${form.id ? '编辑' : '新增'}${kind === 'devices' ? '设备' : '工位'}`" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="编号" prop="code" :rules="[{required:true,message:'请填写编号',whitespace:true}]"><el-input v-model="form.code" maxlength="64" /></el-form-item>
        <template v-if="kind === 'devices'">
          <el-form-item label="型号" prop="model" :rules="[{required:true,message:'请填写型号',whitespace:true}]"><el-input v-model="form.model" maxlength="100" /></el-form-item>
          <el-form-item label="资产状态"><el-select v-model="form.assetState" @change="value => { if(value === 'RETIRED') form.stationId = null }"><el-option v-for="(label,value) in states" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="分配工位"><el-select v-model="form.stationId" clearable filterable :disabled="form.assetState === 'RETIRED'" @clear="form.stationId = null"><el-option v-for="row in data.stations" :key="row.id" :value="row.id" :label="`${row.code} · ${row.location}`" /></el-select></el-form-item>
        </template>
        <el-form-item v-else label="位置" prop="location" :rules="[{required:true,message:'请填写位置',whitespace:true}]"><el-input v-model="form.location" maxlength="200" /></el-form-item>
        <el-form-item label="联系方式"><el-input v-model="form.contact" maxlength="100" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="opened=false" :disabled="saving">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup name="Equipment">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, Download } from '@element-plus/icons-vue'
import request from '@/utils/request'
const kind=ref('devices'), data=ref({devices:[],stations:[]}), search=ref(''), state=ref(''), page=ref(1)
const loading=ref(false), failed=ref(false), saving=ref(false), opened=ref(false), form=ref({}), formRef=ref()
const states={AVAILABLE:'可用',MAINTENANCE:'维修中',RETIRED:'已报废'}
const rows=computed(() => data.value[kind.value].filter(row =>
  (kind.value !== 'devices' || !state.value || row.assetState === state.value) &&
  [row.code,row.model,row.location,row.contact,row.stationCode].join(' ').toLowerCase().includes(search.value.trim().toLowerCase())))
function exportRows() {
  const device=kind.value==='devices'
  const header=device ? ['编号','型号','资产状态','工位','联系方式','备注'] : ['编号','位置','联系方式','备注']
  const values=rows.value.map(row => device ? [row.code,row.model,states[row.assetState],row.stationCode,row.contact,row.remark] : [row.code,row.location,row.contact,row.remark])
  const cell=value => {
    let text=String(value ?? '')
    if (/^[\s]*[=+\-@]/.test(text)) text="'"+text
    return '"'+text.replaceAll('"','""')+'"'
  }
  const blob=new Blob(['\uFEFF'+[header,...values].map(row=>row.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'})
  const url=URL.createObjectURL(blob), link=document.createElement('a')
  link.href=url; link.download=device?'设备台账.csv':'考场工位.csv'; link.click()
  setTimeout(()=>URL.revokeObjectURL(url),1000)
}
async function load() {
  loading.value=true
  try { data.value=(await request.get('/system/equipment')).data; failed.value=false }
  catch { failed.value=true }
  finally { loading.value=false }
}
function edit(row) {
  form.value=row ? {...row} : {code:'',model:'',assetState:'AVAILABLE',stationId:null,location:'',contact:'',remark:''}
  opened.value=true
}
async function save() {
  if(saving.value || !await formRef.value.validate().catch(() => false)) return
  saving.value=true
  try {
    await request.post(`/system/equipment/${kind.value}`,{...form.value,code:form.value.code.trim(),stationId:form.value.stationId || null})
    opened.value=false; ElMessage.success('已保存'); await load()
  } finally { saving.value=false }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`删除 ${row.code}？`,'确认删除',{type:'warning'}) } catch { return }
  await request.delete(`/system/equipment/${kind.value}/${row.id}`,{params:{revision:row.revision}})
  ElMessage.success('已删除'); await load(); page.value=Math.min(page.value,Math.max(1,Math.ceil(rows.value.length/20)))
}
onMounted(load)
</script>
<style scoped>
.equipment-page { color: #263238; background: #fff; min-height: calc(100vh - 84px); }
header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
h2 { font-size:20px; margin:0; }
.toolbar { display:flex; gap:12px; margin:8px 0 18px; }
.toolbar .el-input { width:300px; }
.toolbar .el-select { width:160px; }
.el-pagination { margin-top:18px; justify-content:flex-end; }
.el-form .el-select { width:100%; }
</style>
