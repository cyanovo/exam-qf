<template>
  <section class="python-workbench">
    <div class="toolbar">
      <el-button v-if="saveDraft" :disabled="disabled || busy || saving" :loading="saving" icon="Document" @click="save">保存草稿</el-button>
      <el-button v-if="!manual" :disabled="disabled || busy" icon="CaretRight" @click="submit('SAMPLE')">运行样例（不计分）</el-button>
      <el-button :disabled="disabled || busy" type="primary" @click="submit('FORMAL')">{{ manual ? '提交代码' : '运行并评分（正式提交）' }}</el-button>
      <el-select v-model="theme" aria-label="编辑器主题" class="theme">
        <el-option label="浅色" value="vs" /><el-option label="深色" value="vs-dark" />
      </el-select>
      <el-input-number v-model="fontSize" :min="12" :max="24" aria-label="字号" controls-position="right" />
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="!manual && latestFormal" class="formal-result">
      最近一次正式提交 {{ latestFormal.id }}：
      <template v-if="latestFormal.state === 'SUCCEEDED' && latestFormal.score != null">
        <strong :class="{ incomplete: latestFormal.score < maxScore }">{{ latestFormal.score }} / {{ maxScore }} 分</strong>
        <span> · {{ latestFormal.score === maxScore ? '全部隐藏测试通过' : '未通过全部隐藏测试' }}</span>
      </template>
      <span v-else>{{ states[latestFormal.state] || latestFormal.state }}</span>
    </p>
    <div ref="host" class="editor" aria-label="Python代码编辑器"></div>
    <div class="samples">
      <div v-for="(sample, index) in samples" :key="index">
        <h4>公开样例 {{ index + 1 }}</h4>
        <div class="sample-grid"><div><b>输入</b><pre>{{ sample.input }}</pre></div><div><b>预期输出</b><pre>{{ sample.expected }}</pre></div></div>
      </div>
    </div>
    <h4>提交记录</h4>
    <el-table :data="history" size="small" highlight-current-row @current-change="selectHistory">
      <el-table-column prop="id" label="提交编号" width="100" />
      <el-table-column label="类型" width="100"><template #default="{ row }">{{ row.mode === 'SAMPLE' ? '公开样例' : '正式提交' }}</template></el-table-column>
      <el-table-column label="状态" min-width="160"><template #default="{ row }">{{ manual ? '已提交裁判' : resultLabel(row) }}</template></el-table-column>
      <el-table-column v-if="!manual" label="本次得分" width="110">
        <template #default="{ row }">{{ row.mode === 'FORMAL' && row.state === 'SUCCEEDED' && row.score != null ? `${row.score} / ${maxScore}` : '—' }}</template>
      </el-table-column>
      <el-table-column prop="receivedAt" label="接收时间" min-width="160" />
    </el-table>
    <section v-if="!manual && selectedSample" class="output">
      <h4>公开样例运行（仅验证样例，不代表正式得分） · 提交 {{ selectedSample.id }} · {{ states[selectedSample.state] || selectedSample.state }}</h4>
      <p v-if="!feedback.length" class="pending-output">{{ selectedSample.state === 'FAILED' ? '评测服务暂时不可用，请稍后重试' : '正在等待公开样例结果…' }}</p>
      <div v-for="item in feedback" :key="item.case">
        <h4>样例 {{ item.case }} · {{ item.passed ? '样例通过（不计分）' : '样例未通过' }} · {{ verdictLabel(item) }}</h4>
        <pre>{{ item.stdout || '(无标准输出)' }}</pre><pre v-if="item.stderr || item.compileOutput" class="error-output">{{ item.stderr }}{{ item.compileOutput }}</pre>
      </div>
    </section>
  </section>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import * as monaco from 'monaco-editor/editor/editor.api.js'
import 'monaco-editor/languages/definitions/python/register.js'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import request from '@/utils/request'

self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
const props = defineProps({ modelValue: { type: String, default: '' }, examId: [String, Number], questionId: [String, Number],
  snapshotHash: String, maxScore: Number, samples: { type: Array, default: () => [] }, disabled: Boolean, manual: Boolean, saveDraft: Function })
const emit = defineEmits(['update:modelValue', 'submitted', 'busy'])
const host = ref(null), busy = ref(false), saving = ref(false), error = ref(''), history = ref([]), selectedSampleId = ref(null)
const selectedSample = computed(() => history.value.find(row => row.id === selectedSampleId.value && row.mode === 'SAMPLE'))
const latestFormal = computed(() => history.value.find(row => row.mode === 'FORMAL'))
const feedback = computed(() => selectedSample.value?.feedback || [])
const theme = ref('vs'), fontSize = ref(15)
const states = { QUEUED: '排队中', RUNNING: '评测中', RETRY: '系统重试中', SUCCEEDED: '评测完成', FAILED: '系统失败 · 等待重试' }
function resultLabel(row) {
  if (row.state !== 'SUCCEEDED') return states[row.state] || row.state
  if (row.mode === 'SAMPLE') return '样例运行完成（不计分）'
  return row.score == null ? '评测完成' : row.score === props.maxScore ? '全部隐藏测试通过' : '未通过全部隐藏测试'
}
const verdicts = { Accepted: '运行完成', 'Time Limit Exceeded': '运行超时', 'Memory Limit Exceeded': '内存超限',
  'Output Limit Exceeded': '输出超限', 'Nonzero Exit Status': '运行异常', 'Non Zero Exit Status': '运行异常', Signalled: '运行中断',
  'Dangerous Syscall': '不允许的系统调用', 'Runtime/Compilation Error': '语法或运行错误' }
const verdictLabel = item => verdicts[item.verdict] || ({ 3: '运行完成', 5: '运行超时', 6: '语法错误' })[item.status] || '运行异常'
let editor, timer, disposed = false, pending = null, historyVersion = 0
watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value) })
watch(() => props.disabled, value => editor?.updateOptions({ readOnly: value }))
watch(fontSize, value => editor?.updateOptions({ fontSize: value }))
watch(theme, value => monaco.editor.setTheme(value))

async function refresh() {
  const version = historyVersion
  try {
    const response = await request.get(props.manual
      ? `/exam/practical/${props.examId}/${props.questionId}/history`
      : `/exam/code/${props.examId}/history/${props.questionId}`)
    if (disposed || version !== historyVersion) return
    history.value = response.data
    if (props.manual && history.value.length) emit('submitted', history.value[0])
    if (!history.value.some(row => row.id === selectedSampleId.value && row.mode === 'SAMPLE')) {
      selectedSampleId.value = history.value.find(row => row.mode === 'SAMPLE')?.id ?? null
    }
  } catch { if (!disposed) error.value = '提交记录加载失败，稍后自动重试' }
  finally { if (!disposed) timer = setTimeout(refresh, 4000) }
}
function selectHistory(row) {
  if (row?.mode === 'SAMPLE') selectedSampleId.value = row.id
}
async function save() {
  if (!props.saveDraft || saving.value || props.disabled) return
  saving.value = true
  error.value = ''
  try {
    if (!await props.saveDraft()) throw new Error('草稿保存失败，请重试')
    ElMessage.success('草稿已保存')
  } catch (failure) {
    error.value = failure.message || '草稿保存失败，请重试'
  } finally { saving.value = false }
}
async function submit(mode) {
  if (busy.value || props.disabled) return
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    if (!crypto.subtle || !crypto.randomUUID) throw new Error('代码提交需要 HTTPS 安全连接')
    if (!props.modelValue.trim()) throw new Error('请先填写代码')
    const source = props.modelValue
    if (new TextEncoder().encode(source).length > 60000) throw new Error('代码不能超过60KB')
    if (mode === 'FORMAL') await ElMessageBox.confirm(props.manual
      ? '代码将提交给现场裁判。裁判开始评分后不能更换正式代码。'
      : '将运行隐藏测试并在评测完成后显示本次得分。本次代码作为正式提交保存，以截止前最后一次正式提交计分。', '确认正式提交')
    if (props.disabled) throw new Error('考试已截止或正在交卷')
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(source))
    const sourceHash = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('')
    if (!pending || pending.sourceHash !== sourceHash || pending.mode !== mode) {
      pending = { questionId: props.questionId, mode, source, sourceHash, snapshotHash: props.snapshotHash, key: crypto.randomUUID() }
    }
    const response = await request.post(props.manual
      ? `/exam/practical/${props.examId}/${props.questionId}/submit`
      : `/exam/code/${props.examId}`, pending, { headers: { repeatSubmit: false } })
    pending = null
    historyVersion++
    history.value = [response.data, ...history.value.filter(row => row.id !== response.data.id)]
    if (mode === 'SAMPLE') selectedSampleId.value = response.data.id
    if (props.manual) emit('submitted', response.data)
    ElMessage.success(`已接收提交 ${response.data.id}`)
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '提交失败，可重试'
  } finally { busy.value = false; emit('busy', false) }
}
onMounted(() => {
  editor = monaco.editor.create(host.value, { value: props.modelValue, language: 'python', theme: theme.value,
    fontSize: fontSize.value, readOnly: props.disabled, automaticLayout: true, minimap: { enabled: false },
    scrollBeyondLastLine: false, tabSize: 4, wordWrap: 'on' })
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor.getValue()))
  refresh()
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
  const model = editor?.getModel()
  editor?.dispose()
  model?.dispose()
})
</script>
<style scoped>
.python-workbench { min-width: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.toolbar .el-button { margin-left: 0; }.theme { width: 100px; }.toolbar .el-input-number { width: 100px; }
.editor { height: 400px; min-width: 0; border: 1px solid #aaa; }
.sample-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
h4 { margin: 18px 0 10px; font-size: 14px; } pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: #f3f4f5; }
.error-output { color: #ab2630; } .output { border-top: 1px solid #ddd; }
.formal-result { margin: 0 0 12px; color: var(--el-text-color-regular); }
.formal-result strong { color: var(--el-color-success); }
.formal-result strong.incomplete { color: var(--el-color-danger); }
.pending-output { color: var(--el-text-color-secondary); }
@media (max-width: 600px) { .sample-grid { grid-template-columns: minmax(0, 1fr); gap: 0; } }
</style>
