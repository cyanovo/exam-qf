<template>
  <div class="grading-rules">
    <el-alert v-if="invalid" title="评分规则无法解析，请重新配置" type="error" :closable="false" />
    <template v-if="type === 'MULTI_CHOICE'">
      <el-radio-group v-model="rules.mode" @change="save">
        <el-radio value="EXACT" label="EXACT">全对得分</el-radio>
        <el-radio value="PARTIAL" label="PARTIAL">少选部分得分，错选零分</el-radio>
      </el-radio-group>
      <label v-if="rules.mode === 'PARTIAL'">少选得分
        <el-input-number v-model="rules.partialScore" :min="1" :max="99" :precision="0" @change="save" />
      </label>
    </template>
    <template v-else>
      <el-select v-model="rules.mode" aria-label="填空评分方式" @change="save">
        <el-option label="人工批改" value="MANUAL" />
        <el-option label="文本自动评分" value="TEXT" />
        <el-option label="数值自动评分" value="NUMERIC" />
      </el-select>
      <template v-if="rules.mode !== 'MANUAL'">
        <el-radio-group v-model="rules.scoring" @change="save">
          <el-radio label="ALL" value="ALL">全部正确得分</el-radio>
          <el-radio label="PER_BLANK" value="PER_BLANK">每空等分</el-radio>
        </el-radio-group>
        <template v-if="rules.mode === 'TEXT'">
          <el-select v-model="rules.whitespace" aria-label="空格处理" @change="save">
            <el-option label="空格精确匹配" value="EXACT" />
            <el-option label="忽略首尾空格" value="TRIM" />
            <el-option label="忽略所有空格" value="REMOVE" />
          </el-select>
          <el-checkbox v-model="rules.ignoreCase" @change="save">忽略大小写</el-checkbox>
          <el-checkbox v-model="rules.normalizeWidth" @change="save">兼容全角字符</el-checkbox>
        </template>
        <label v-else>数值容差
          <el-input-number v-model="rules.tolerance" :min="0" :max="1" :step="0.001" :precision="6" @change="save" />
        </label>
        <div v-for="(blank, index) in rules.accepted" :key="index" class="blank">
          <span>第 {{ index + 1 }} 空</span>
          <el-select v-model="rules.accepted[index]" multiple filterable allow-create default-first-option
            :reserve-keyword="false" placeholder="可接受答案" @change="save" />
          <el-tooltip content="删除此空">
            <el-button icon="Delete" aria-label="删除此空" :disabled="rules.accepted.length === 1"
              @click="rules.accepted.splice(index, 1); save()" />
          </el-tooltip>
        </div>
        <el-button icon="Plus" :disabled="rules.accepted.length >= 20" @click="rules.accepted.push([]); save()">添加填空</el-button>
      </template>
    </template>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ modelValue: String, type: String, answer: String })
const emit = defineEmits(['update:modelValue'])
const rules = ref({}), invalid = ref(false)
let last
watch(() => [props.modelValue, props.type], ([value]) => {
  if (value === last && rules.value.mode) return
  const defaults = { version: 1, mode: props.type === 'MULTI_CHOICE' ? 'EXACT' : 'MANUAL',
    partialScore: 1, scoring: 'ALL', whitespace: 'TRIM', ignoreCase: false,
    normalizeWidth: false, tolerance: 0, accepted: (props.answer || '').split(';').map(s => s ? [s] : []) }
  try { rules.value = value ? { ...defaults, ...JSON.parse(value) } : defaults; invalid.value = false }
  catch { rules.value = defaults; invalid.value = true }
}, { immediate: true })
function save() {
  last = JSON.stringify(rules.value)
  invalid.value = false
  emit('update:modelValue', last)
}
</script>
<style scoped>
.grading-rules { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; width: 100%; min-width: 0; }
.grading-rules > .el-select { width: 220px; max-width: 100%; }
.blank { display: flex; gap: 8px; align-items: center; width: 100%; }
.blank > span { flex: 0 0 60px; }
.blank .el-select { flex: 1; min-width: 0; }
.grading-rules .el-radio-group { display: flex; flex-wrap: wrap; }
</style>
