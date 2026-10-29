<template>
  <div class="app-container exam-app">
    <!-- 搜索区域 -->
    <div class="search-bar">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
        <el-form-item label="题目分类" prop="category">
          <el-select v-model="queryParams.category" placeholder="请选择" clearable>
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难易程度" prop="difficulty">
          <el-select v-model="queryParams.difficulty" placeholder="请选择" clearable>
            <el-option v-for="i in 5" :key="i" :label="i + '星'" :value="i" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目类型" prop="questionType">
          <el-select v-model="queryParams.questionType" placeholder="请选择" clearable>
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['question:question:add']">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['question:question:edit']">修改</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['question:question:remove']">删除</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['question:question:export']">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['question:question:import']">导入</el-button>
        </el-col>
        <el-col :span="1.5">
          <!-- AI 导入按钮 – 炫彩光效 -->
          <el-button class="ai-import-btn" plain icon="Upload" @click="handlePasteImport" v-hasPermi="['question:question:import']">
            AI 导入
          </el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>
    </div>

    <!-- 表格 -->
    <div class="table-wrap">
      <el-table v-loading="loading" :data="questionList" @selection-change="handleSelectionChange" stripe border header-cell-class-name="table-header">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="ID" align="center" prop="questionId" width="80" />
        <el-table-column label="分类" align="center" prop="category" width="100" />
        <el-table-column label="难度" align="center" width="120">
          <template #default="{ row }">
            <el-rate v-model="row.difficulty" disabled show-score text-color="#ff9900" />
          </template>
        </el-table-column>
        <el-table-column label="题型" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.questionType)" effect="light">{{ typeLabel(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题干" prop="title" show-overflow-tooltip min-width="200" />
        <el-table-column label="答案" align="center" width="120" prop="answer" />
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="0" :inactive-value="1" disabled inline-prompt active-text="启" inactive-text="停" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['question:question:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['question:question:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </div>

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="750px" append-to-body destroy-on-close class="modern-dialog">
      <el-form ref="questionRef" :model="form" :rules="rules" label-width="100px" class="exam-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="题目分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择" style="width:100%">
                <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难易程度" prop="difficulty">
              <el-rate v-model="form.difficulty" :max="5" show-score />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目类型" prop="questionType">
          <el-radio-group v-model="form.questionType" @change="onTypeChange">
            <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="题干" prop="title">
          <el-input v-model="form.title" type="textarea" :rows="3" placeholder="请输入题干" class="limited-textarea" />
        </el-form-item>

        <!-- 选项编辑区 -->
        <el-form-item v-if="isChoiceOrJudge" label="选项" prop="subTitle">
          <div class="option-list">
            <div v-for="(opt, index) in optionList" :key="index" class="option-item">
              <el-input v-model="opt.label" style="width:60px" disabled />
              <el-input v-model="opt.content" placeholder="选项内容" style="width:320px; margin:0 10px" />
              <el-checkbox v-if="form.questionType === 'MULTI_CHOICE'" v-model="opt.isAnswer" :true-label="1" :false-label="0" style="margin-right:10px">正确</el-checkbox>
              <el-radio v-else v-model="singleCorrect" :label="opt.label" @change="onSingleAnswerChange(index)" style="margin-right:10px">正确</el-radio>
              <el-button icon="Delete" circle size="small" @click="removeOption(index)" :disabled="optionList.length <= 2 && form.questionType === 'JUDGE'" />
            </div>
            <el-button type="primary" text @click="addOption" v-if="form.questionType !== 'JUDGE'">+ 添加选项</el-button>
          </div>
        </el-form-item>

        <!-- 填空/简答答案 -->
        <el-form-item v-if="form.questionType === 'PYTHON'" label="测试用例" prop="answer">
          <CodingCases v-model="form.answer" />
        </el-form-item>
        <el-form-item v-if="isFillOrShort" label="答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" class="limited-textarea" :placeholder="form.questionType === 'FILL_BLANK' ? '多个填空答案请用英文分号(;)分隔' : '请输入参考答案'" />
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" class="limited-textarea" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- AI 智能导入对话框 -->
    <el-dialog title="AI 智能导入" v-model="pasteOpen" width="800px" append-to-body class="modern-dialog">
      <el-alert title="操作指南" type="info" :closable="false" show-icon class="step-alert">
        <template #default>
          <div class="steps">
            <p>① 在下文粘贴您的试题文本（支持自然语言、Word 复制内容等）</p>
            <p>② 填写您的 DeepSeek API Key（<a href="https://platform.deepseek.com/api_keys" target="_blank">获取地址</a>）</p>
            <p>③ 点击“生成试题”，AI 自动转换为标准 JSON</p>
            <p>④ 预览并编辑 JSON，确认后点击“导入”批量入库</p>
          </div>
        </template>
      </el-alert>

      <div style="margin-top: 20px;">
        <el-form>
          <el-form-item label="试题内容">
            <el-input 
              v-model="rawExamText" 
              type="textarea" 
              :rows="8" 
              placeholder="请将您的试题粘贴到这里，例如：
    1. Java 语言的特点有哪些？（单选题）
    A. 面向对象  B. 跨平台  C. 编译型  D. 以上都对
    答案：D

    2. Python 中用于定义函数的关键字是？（填空题）
    答案：def"
              class="limited-textarea"
            />
          </el-form-item>

          <el-form-item label="API Key">
            <el-input 
              v-model="apiKey" 
              show-password 
              placeholder="sk-xxxxxxxxxxxxxxxxxxxxxxxx"
            >
              <template #append>
                <el-button @click="saveApiKey">保存</el-button>
              </template>
            </el-input>
            <div style="font-size: 12px; color: #999; margin-top: 4px;">
              密钥仅保存在当前浏览器，不会上传至服务器。
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="generating" @click="generateQuestionsByAI">
              <el-icon v-if="!generating"><MagicStick /></el-icon>
              {{ generating ? '正在调用 AI...' : '生成试题' }}
            </el-button>
            <el-button @click="clearAIForm">清空</el-button>
          </el-form-item>
        </el-form>

        <!-- 生成结果预览 -->
        <div v-if="generatedJson" class="ai-preview">
          <el-divider content-position="left">
            <span style="font-weight: 600; color: #303133;">AI 生成结果预览</span>
          </el-divider>
          <el-input
            v-model="jsonText"
            type="textarea"
            :rows="15"
            placeholder="JSON 数据"
            class="limited-textarea"
          />
          <div class="preview-actions">
            <el-button type="primary" @click="submitJsonImport" :disabled="!jsonText">
              导入预览数据
            </el-button>
            <el-button @click="copyGeneratedJson">复制 JSON</el-button>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="pasteOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Question">
import CodingCases from './CodingCases.vue'
import { listQuestion, getQuestion, delQuestion, addQuestion, updateQuestion, importJson } from "@/api/question/question"
import { getCurrentInstance, reactive, ref, toRefs, computed, nextTick } from "vue"
import { getToken } from "@/utils/auth"
import request from '@/utils/request'
import { Upload, MagicStick } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance()

const importOpen = ref(false)
const uploadRef = ref(null)
const questionList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

// AI 导入相关变量
const pasteOpen = ref(false)
const jsonText = ref('')
const rawExamText = ref('')
const apiKey = ref(localStorage.getItem('deepseek_api_key') || '')
const generating = ref(false)
const generatedJson = ref('')

const data = reactive({
  form: {
    questionId: null,
    category: "",
    difficulty: 1,
    questionType: "SINGLE_CHOICE",
    title: "",
    subTitle: "",
    answer: "",
    status: 0,
    remark: ""
  },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    category: undefined,
    difficulty: undefined,
    questionType: undefined
  },
  rules: {
    category: [{ required: true, message: "题目分类不能为空", trigger: "change" }],
    difficulty: [{ required: true, message: "难易程度不能为空", trigger: "change" }],
    questionType: [{ required: true, message: "题目类型不能为空", trigger: "change" }],
    title: [{ required: true, message: "题干不能为空", trigger: "blur" }],
    answer: [{
      validator: (rule, value, callback) => {
        if (isChoiceOrJudge.value) callback()
        else if (!value || value.trim() === "") callback(new Error("答案不能为空"))
        else callback()
      },
      trigger: "blur"
    }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const categoryOptions = [
  { label: "Java", value: "JAVA" },
  { label: "Python", value: "PYTHON" },
  { label: "C语言", value: "C" },
  { label: "HTML", value: "HTML" }
]

const typeOptions = [
  { label: "单选题", value: "SINGLE_CHOICE" },
  { label: "多选题", value: "MULTI_CHOICE" },
  { label: "判断题", value: "JUDGE" },
  { label: "填空题", value: "FILL_BLANK" },
  { label: "简答题", value: "SHORT_ANSWER" },
  { label: "Python编程", value: "PYTHON" }
]

const typeLabel = (type) => {
  const map = { SINGLE_CHOICE: "单选题", MULTI_CHOICE: "多选题", JUDGE: "判断题", FILL_BLANK: "填空题", SHORT_ANSWER: "简答题", PYTHON: "Python编程" }
  return map[type] || type
}
const typeTag = (type) => {
  const map = { SINGLE_CHOICE: "", MULTI_CHOICE: "success", JUDGE: "warning", FILL_BLANK: "info", SHORT_ANSWER: "danger" }
  return map[type] || ""
}

const optionList = ref([])
const singleCorrect = ref("")
const isChoiceOrJudge = computed(() => ["SINGLE_CHOICE", "MULTI_CHOICE", "JUDGE"].includes(form.value.questionType))
const isFillOrShort = computed(() => ["FILL_BLANK", "SHORT_ANSWER"].includes(form.value.questionType))

function initOptionsForType() {
  if (form.value.questionType === "JUDGE") {
    optionList.value = [
      { label: "A", content: "对", isAnswer: 0 },
      { label: "B", content: "错", isAnswer: 0 }
    ]
  } else if (["SINGLE_CHOICE", "MULTI_CHOICE"].includes(form.value.questionType)) {
    optionList.value = [
      { label: "A", content: "", isAnswer: 0 },
      { label: "B", content: "", isAnswer: 0 },
      { label: "C", content: "", isAnswer: 0 },
      { label: "D", content: "", isAnswer: 0 }
    ]
  } else {
    optionList.value = []
  }
  singleCorrect.value = ""
}

function onTypeChange() {
  if (["SINGLE_CHOICE", "MULTI_CHOICE", "JUDGE"].includes(form.value.questionType)) {
    if (form.value.subTitle) {
      try {
        const parsed = JSON.parse(form.value.subTitle)
        if (Array.isArray(parsed) && parsed.length) {
          optionList.value = parsed
          const correctOpt = parsed.find(o => o.isAnswer == 1)
          singleCorrect.value = correctOpt ? correctOpt.label : ""
          return
        }
      } catch (e) {}
    }
    initOptionsForType()
  } else {
    optionList.value = []
    singleCorrect.value = ""
    form.value.subTitle = ""
  }
}

function addOption() {
  const labels = "ABCDEFGH"
  const nextLabel = labels[optionList.value.length] || String(optionList.value.length + 1)
  optionList.value.push({ label: nextLabel, content: "", isAnswer: 0 })
}

function removeOption(index) {
  optionList.value.splice(index, 1)
  if (singleCorrect.value === optionList.value[index]?.label) singleCorrect.value = ""
}

function onSingleAnswerChange(index) {
  optionList.value.forEach((opt, i) => opt.isAnswer = i === index ? 1 : 0)
}

function syncAnswerFromOptions() {
  if (form.value.questionType === "SINGLE_CHOICE" || form.value.questionType === "JUDGE") {
    const correct = optionList.value.find(o => o.isAnswer == 1)
    form.value.answer = correct ? correct.label : ""
  } else if (form.value.questionType === "MULTI_CHOICE") {
    form.value.answer = optionList.value.filter(o => o.isAnswer == 1).map(o => o.label).join(",")
  }
}

function getList() {
  loading.value = true
  listQuestion(queryParams.value).then(response => {
    questionList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    questionId: null,
    category: "",
    difficulty: 1,
    questionType: "SINGLE_CHOICE",
    title: "",
    subTitle: "",
    answer: "",
    status: 0,
    remark: ""
  }
  optionList.value = []
  singleCorrect.value = ""
  proxy.resetForm("questionRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.questionId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加题库"
  nextTick(() => onTypeChange())
}

function handleUpdate(row) {
  reset()
  const _questionId = row.questionId || ids.value
  getQuestion(_questionId).then(response => {
    form.value = response.data
    if (["SINGLE_CHOICE", "MULTI_CHOICE", "JUDGE"].includes(form.value.questionType)) {
      try {
        optionList.value = form.value.subTitle ? JSON.parse(form.value.subTitle) : []
        if (!optionList.value.length) initOptionsForType()
      } catch (e) { initOptionsForType() }
      const correctOpt = optionList.value.find(o => o.isAnswer == 1)
      singleCorrect.value = correctOpt ? correctOpt.label : ""
    } else {
      optionList.value = []
    }
    open.value = true
    title.value = "修改题库"
  })
}

function submitForm() {
  proxy.$refs["questionRef"].validate(valid => {
    if (!valid) return
    if (isChoiceOrJudge.value) {
      form.value.subTitle = JSON.stringify(optionList.value)
      syncAnswerFromOptions()
    }
    if (form.value.questionId != null) {
      updateQuestion(form.value).then(() => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
    } else {
      addQuestion(form.value).then(() => {
        proxy.$modal.msgSuccess("新增成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const _questionIds = row.questionId || ids.value
  proxy.$modal.confirm('是否确认删除题库编号为"' + _questionIds + '"的数据项？').then(function () {
    return delQuestion(_questionIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  request({
    url: '/question/question/export',
    method: 'get',
    params: { ...queryParams.value, _t: Date.now() },
    responseType: 'blob'
  }).then(blob => {
    const url = window.URL.createObjectURL(blob instanceof Blob ? blob : blob.data)
    const link = document.createElement('a')
    link.href = url
    link.download = '题库数据.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  }).catch(() => {
    proxy.$modal.msgError('导出失败')
  })
}

const uploadUrl = import.meta.env.VITE_APP_BASE_API + "/question/question/import"
const uploadHeaders = { Authorization: "Bearer " + getToken() }

function handleImport() {
  importOpen.value = true
}

function beforeUpload(file) {
  const isExcel = file.type === "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                  file.type === "application/vnd.ms-excel"
  if (!isExcel) {
    proxy.$modal.msgError("只能上传 Excel 文件！")
    return false
  }
  return true
}

function submitUpload() {
  uploadRef.value.submit()
}

function handleImportSuccess(res) {
  if (res.code === 200) {
    proxy.$modal.msgSuccess("导入成功")
    importOpen.value = false
    getList()
  } else {
    proxy.$modal.msgError(res.msg || "导入失败")
  }
}

function downloadTemplate() {
  request({
    url: '/question/question/importTemplate',
    method: 'get',
    responseType: 'blob'
  }).then(blob => {
    const url = window.URL.createObjectURL(blob instanceof Blob ? blob : blob.data)
    const link = document.createElement('a')
    link.href = url
    link.download = '题库导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  }).catch(() => {
    proxy.$modal.msgError('模板下载失败')
  })
}

function handlePasteImport() {
  pasteOpen.value = true
  jsonText.value = ''
  rawExamText.value = ''
  generatedJson.value = ''
}

function saveApiKey() {
  localStorage.setItem('deepseek_api_key', apiKey.value)
  proxy.$modal.msgSuccess('API Key 已保存')
}

function clearAIForm() {
  rawExamText.value = ''
  jsonText.value = ''
  generatedJson.value = ''
}

async function generateQuestionsByAI() {
  if (!rawExamText.value.trim()) {
    proxy.$modal.msgWarning('请粘贴试题内容')
    return
  }
  if (!apiKey.value.trim()) {
    proxy.$modal.msgWarning('请填写 DeepSeek API Key')
    return
  }

  generating.value = true
  try {
    const systemPrompt = `你是一个专业的题库转换助手。请将用户提供的试题内容转换为 JSON 数组格式。
要求：
1. 每个题目一个对象，包含字段：
   - category: 题目分类，根据内容推测（JAVA/PYTHON/C/HTML）
   - difficulty: 难易程度 (1-5整数，默认3)
   - questionType: 题目类型 (SINGLE_CHOICE/MULTI_CHOICE/JUDGE/FILL_BLANK/SHORT_ANSWER)
   - title: 题干内容
   - subTitle: 选择题/判断题：选项文本格式为 'A: 内容; B: 内容 | 正确答案标签'，如 'A: 面向对象; B: 跨平台; C: 编译型; D: 以上都对 | D'。多选题答案用逗号分隔。填空题和简答题此字段为空。
   - answer: 选择题选项标签；多选题标签逗号连接；判断题“对”或“错”；填空题多个答案用英文分号分隔；简答题参考答案。
2. 直接输出 JSON 数组，不要包含 markdown 标记或任何说明文字示例：
[
  {
    "category": "JAVA",
    "difficulty": 3,
    "questionType": "SINGLE_CHOICE",
    "title": "Java 属于什么类型的语言？",
    "subTitle": "A: 编译型语言; B: 解释型语言; C: 编译与解释结合; D: 以上都对 | C",
    "answer": "C"
  }
]。
3. 如果用户没有指定题目分类，智能推测。
4. 尽量保持选项顺序与原文一致。
5. json数组中每一个字段都要有值。
6. 选择题和判断题的选项是写在副题干的，这个一定要有`

    const response = await fetch('https://api.deepseek.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey.value}`
      },
      body: JSON.stringify({
        model: 'deepseek-chat',
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: rawExamText.value }
        ],
        temperature: 0.2
      })
    })

    const data = await response.json()
    if (!response.ok) {
      throw new Error(data.error?.message || '调用失败')
    }

    const content = data.choices?.[0]?.message?.content || ''
    const jsonMatch = content.match(/\[[\s\S]*\]/)
    if (!jsonMatch) {
      throw new Error('AI 返回内容未包含有效 JSON 数组')
    }
    generatedJson.value = jsonMatch[0]
    jsonText.value = generatedJson.value
    proxy.$modal.msgSuccess('试题生成成功，请预览确认')
  } catch (error) {
    console.error(error)
    proxy.$modal.msgError('生成失败：' + error.message)
  } finally {
    generating.value = false
  }
}

function copyGeneratedJson() {
  if (!jsonText.value) return
  navigator.clipboard.writeText(jsonText.value).then(() => {
    proxy.$modal.msgSuccess('已复制到剪贴板')
  }).catch(() => {
    proxy.$modal.msgError('复制失败，请手动选择')
  })
}

function submitJsonImport() {
  if (!jsonText.value.trim()) {
    proxy.$modal.msgWarning("请粘贴 JSON 数据")
    return
  }
  importJson({ json: jsonText.value }).then(res => {
    if (res.code === 200) {
      proxy.$modal.msgSuccess("导入成功")
      pasteOpen.value = false
      getList()
    } else {
      proxy.$modal.msgError(res.msg)
    }
  }).catch(() => {
    proxy.$modal.msgError("导入请求失败")
  })
}

getList()
</script>

<style scoped>
.exam-app {
  padding: 0;
}

.search-bar,
.toolbar,
.table-wrap {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 16px rgba(0, 137, 123, 0.06);
  border: 1px solid rgba(0, 137, 123, 0.06);
}

.table-wrap {
  padding-bottom: 10px;
}

:deep(.table-header) {
  background-color: #e0f2f1;
  font-weight: 600;
  color: #00695c;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th) {
  background-color: #e0f2f1;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #fafbfd;
}

/* 表格行高统一紧凑 */
:deep(.el-table__body td) {
  padding: 10px 0;
}

/* 强制表格内文本单行省略（题干、答案等） */
:deep(.el-table .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-height: 40px;
  line-height: 1.5;
}

/* 操作列按钮保持水平排列 */
:deep(.el-table td:last-child .cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

/* 选项列表 */
.option-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

/* 对话框圆角 */
:deep(.modern-dialog) {
  border-radius: 16px;
}

:deep(.modern-dialog .el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.modern-dialog .el-dialog__header) {
  background-color: #e0f2f1;
  padding: 20px 24px 0;
  border-bottom: none;
}

:deep(.modern-dialog .el-dialog__body) {
  padding: 20px 24px;
}

/* AI 导入按钮炫彩光效 */
.ai-import-btn {
  position: relative;
  z-index: 0;
  border: none;
  color: #fff;
  background: linear-gradient(45deg, #ff0080, #ff8c00, #40e0d0);
  background-size: 300% 300%;
  animation: gradientShift 4s ease infinite;
  box-shadow: 0 0 15px rgba(255, 0, 128, 0.6), 0 0 30px rgba(64, 224, 208, 0.4);
  transition: transform 0.2s ease;
}

.ai-import-btn:hover {
  transform: scale(1.03);
  box-shadow: 0 0 20px rgba(255, 0, 128, 0.8), 0 0 40px rgba(64, 224, 208, 0.6);
}

.ai-import-btn:active {
  transform: scale(0.98);
}

@keyframes gradientShift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

/* 提示词操作区 */
.prompt-actions {
  margin: 15px 0;
  display: flex;
  gap: 10px;
  align-items: center;
}

.ai-preview {
  margin-top: 20px;
}

.preview-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.steps a {
  color: #00897b;
  text-decoration: none;
  font-weight: 500;
}

.prompt-content {
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
}

.steps p {
  margin: 5px 0;
}

.ai-link {
  margin-top: 8px;
  color: #00897b;
}

.ai-link a {
  color: #00897b;
  font-weight: 500;
}

:deep(.el-button) {
  border-radius: 10px;
}
</style>
