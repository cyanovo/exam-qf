<template>
  <div class="case-editor">
    <el-alert v-if="invalid" type="error" title="现有测试点数据无法解析，修改后将替换原配置" :closable="false" />
    <div v-for="(item, index) in cases" :key="index" class="case">
      <div class="case-toolbar">
        <b>测试点 {{ index + 1 }}</b>
        <el-checkbox v-model="item.public" @change="item.weight = item.public ? 0 : 10; save()">公开样例</el-checkbox>
        <el-input-number v-model="item.weight" :disabled="item.public" :min="0" :max="100" aria-label="分值" @change="save" />
        <el-select v-model="item.comparison" aria-label="比对方式" @change="save">
          <el-option label="精确文本" value="EXACT" /><el-option label="数值容差" value="NUMERIC" /><el-option label="JSON结构" value="JSON" />
        </el-select>
        <el-input-number v-if="item.comparison === 'NUMERIC'" v-model="item.tolerance" :min="0" :max="1" :step="0.001" :precision="6" aria-label="容差" @change="save" />
        <el-tooltip content="删除测试点"><el-button icon="Delete" aria-label="删除测试点" @click="cases.splice(index, 1); save()" /></el-tooltip>
      </div>
      <div class="io"><el-input v-model="item.input" type="textarea" placeholder="标准输入" :rows="3" @input="save" />
        <el-input v-model="item.expected" type="textarea" placeholder="预期输出" :rows="3" @input="save" /></div>
    </div>
    <el-button icon="Plus" @click="add">添加测试点</el-button><span class="total">隐藏用例合计 {{ total }} 分</span>
  </div>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
const props = defineProps({ modelValue: String })
const emit = defineEmits(['update:modelValue'])
const cases = ref([]), invalid = ref(false)
let last
watch(() => props.modelValue, value => {
  if (value === last) return
  try { cases.value = value ? JSON.parse(value).cases : []; if (!Array.isArray(cases.value)) throw new Error(); invalid.value = false }
  catch { cases.value = []; invalid.value = !!value }
}, { immediate: true })
const total = computed(() => cases.value.filter(c => !c.public).reduce((sum, c) => sum + (c.weight || 0), 0))
function save() { last = JSON.stringify({ cases: cases.value }); emit('update:modelValue', last); invalid.value = false }
function add() { cases.value.push({ input: '', expected: '', weight: cases.value.length ? 10 : 0, public: !cases.value.length, comparison: 'EXACT', tolerance: 0.001 }); save() }
</script>
<style scoped>
.case-editor { width: 100%; min-width: 0; }.case { padding: 16px 0; border-bottom: 1px solid #ddd; }
.case-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 10px; }
.case-toolbar .el-select { width: 120px; }.case-toolbar .el-input-number { width: 120px; }
.io { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 10px; }.total { margin-left: 12px; }
</style>
