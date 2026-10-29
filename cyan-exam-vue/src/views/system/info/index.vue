<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="考试名称" prop="examName">
        <el-input v-model="queryParams.examName" placeholder="请输入考试名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker
          v-model="queryParams.startTime"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择开始时间"
          clearable
        />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker
          v-model="queryParams.endTime"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择结束时间"
          clearable
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable>
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['exam:exam:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="!editableSelection" @click="handleUpdate" v-hasPermi="['exam:exam:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!deletableSelection" @click="handleDelete" v-hasPermi="['exam:exam:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['exam:exam:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="User" @click="handleAssignStudent" :disabled="!editableSelection"
          v-hasPermi="['exam:exam:edit']">分配学生</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="List" @click="handleSelectQuestion" :disabled="!editableSelection"
          v-hasPermi="['exam:exam:edit']">选择题目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="CircleCheck" :disabled="!editableSelection" @click="handlePublish" v-hasPermi="['exam:exam:edit']">发布</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="examList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="考试名称" align="center" prop="examName" show-overflow-tooltip />
      <el-table-column label="开始时间" align="center" width="180">
        <template #default="{ row }">
          <span>{{ parseTime(row.startTime, '{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" width="180">
        <template #default="{ row }">
          <span>{{ parseTime(row.endTime, '{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="{ row }">
          <el-switch v-model="row.status" :active-value="'0'" :inactive-value="'1'" disabled />
        </template>
      </el-table-column>
      <el-table-column label="发布" align="center" width="110">
        <template #default="{ row }">{{ row.publishedAt ? "已发布" : "草稿" }}</template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="215">
        <template #default="scope">
          <el-button v-if="canEdit(scope.row)" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['exam:exam:edit']">修改</el-button>
          <el-button v-if="canEdit(scope.row)" link type="primary" icon="CircleCheck" @click="handlePublish(scope.row)" v-hasPermi="['exam:exam:edit']">发布</el-button>
          <el-button v-if="canEdit(scope.row)" link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['exam:exam:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="examRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="考试名称" prop="examName">
          <el-input v-model="form.examName" placeholder="请输入考试名称" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择开始时间"
            clearable
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择结束时间"
            clearable
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 分配学生对话框 -->
    <el-dialog title="分配学生" v-model="studentOpen" width="700px" append-to-body>
      <el-transfer
        v-model="selectedStudentIds"
        :data="allStudentList"
        :titles="['未选学生', '已选学生']"
        filterable
        filter-placeholder="请输入学生姓名/学号"
        :props="{ key: 'userId', label: 'displayName' }"
      />
      <template #footer>
        <el-button @click="studentOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitStudentAssign">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 选择题目对话框（带筛选） -->
    <el-dialog title="选择题目" v-model="questionOpen" width="900px" append-to-body>
      <!-- 题目筛选区域 -->
      <el-form :model="questionQuery" :inline="true" label-width="68px" @submit.prevent>
        <el-form-item label="题目分类">
          <el-select v-model="questionQuery.category" placeholder="请选择" clearable>
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难易程度">
          <el-select v-model="questionQuery.difficulty" placeholder="请选择" clearable>
            <el-option v-for="i in 5" :key="i" :label="i + '星'" :value="i" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目类型">
          <el-select v-model="questionQuery.questionType" placeholder="请选择" clearable>
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadQuestionList">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-transfer
        v-model="selectedQuestionIds"
        :data="allQuestionList"
        :titles="['未选题目', '已选题目']"
        filterable
        filter-placeholder="请输入题干关键词"
        :props="{ key: 'questionId', label: 'title' }"
        style="margin-top: 10px"
      />
      <div v-if="selectedQuestionIds.length" class="question-points">
        <div v-for="questionId in selectedQuestionIds" :key="questionId" class="question-point-row">
          <span>{{ allQuestionList.find(q => q.questionId === questionId)?.title || `题目 ${questionId}` }}</span>
          <el-input-number v-model="questionPoints[questionId]" :min="1" :max="100" :step="1" step-strictly size="small" aria-label="题目满分" />
          <span>分</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="questionOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitQuestionAssign">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CyanExam">
import {
  listCyanExam, getCyanExam, addCyanExam, updateCyanExam, delCyanExam,
  getStudentIds, saveStudents, getQuestionIds, saveQuestions, publishCyanExam,
  getQuestionPoints, saveQuestionPoints
} from "@/api/system/info"
import { listStudent } from "@/api/system/student"       // 学生列表接口（按实际路径调整）
import { listQuestion } from "@/api/question/question"    // 题库列表接口（按实际路径调整）
import { computed, getCurrentInstance, reactive, ref, toRefs } from "vue"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

const examList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const canEdit = row => !row.publishedAt && row.startTime && new Date(row.startTime.replace(' ', 'T')).getTime() > Date.now()
const selectedRows = computed(() => examList.value.filter(row => ids.value.includes(row.examId)))
const editableSelection = computed(() => selectedRows.value.length === 1 && canEdit(selectedRows.value[0]))
const deletableSelection = computed(() => selectedRows.value.length > 0 && selectedRows.value.every(canEdit))
const title = ref("")

// 学生分配相关
const studentOpen = ref(false)
const allStudentList = ref([])
const selectedStudentIds = ref([])

// 题目选择相关
const questionOpen = ref(false)
const allQuestionList = ref([])
const selectedQuestionIds = ref([])
const questionPoints = ref({})

// 题目筛选条件
const questionQuery = reactive({
  category: undefined,
  difficulty: undefined,
  questionType: undefined
})

// 题库分类、题型常量（与题库管理页面一致）
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
  { label: "简答题", value: "SHORT_ANSWER" }
]

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    examName: undefined,
    startTime: undefined,
    endTime: undefined,
    status: undefined
  },
  rules: {
    examName: [{ required: true, message: "考试名称不能为空", trigger: "blur" }],
    startTime: [{ required: true, message: "开始时间不能为空", trigger: "blur" }],
    endTime: [{ required: true, message: "结束时间不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

// 加载题目列表（根据筛选条件）
function loadQuestionList() {
  listQuestion({
    pageNum: 1,
    pageSize: 9999,            // 一次性拉取全部，实际可根据题目数量调整
    category: questionQuery.category,
    difficulty: questionQuery.difficulty,
    questionType: questionQuery.questionType
  }).then(res => {
    allQuestionList.value = res.rows.map(q => ({
      questionId: q.questionId,
      title: `${q.title} (${q.category} | ${['','一星','二星','三星','四星','五星'][q.difficulty]} | ${q.questionType})`
    }))
  })
}

// 查询考试列表
function getList() {
  loading.value = true
  listCyanExam(queryParams.value).then(response => {
    examList.value = response.rows
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
    examId: undefined,
    examName: "",
    startTime: undefined,
    endTime: undefined,
    status: "0",
    remark: ""
  }
  proxy.resetForm("examRef")
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
  ids.value = selection.map(item => item.examId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加考试"
}

function handleUpdate(row) {
  reset()
  const examId = row.examId || ids.value
  getCyanExam(examId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改考试"
  })
}

function handlePublish(row) {
  const examId = row?.examId || ids.value[0]
  if (!examId) return
  proxy.$modal.confirm("发布后将冻结题目、考生和考试时间，确认发布？").then(() => {
    return publishCyanExam(examId)
  }).then(() => {
    proxy.$modal.msgSuccess("发布成功")
    getList()
  }).catch(() => {})
}

function submitForm() {
  proxy.$refs["examRef"].validate(valid => {
    if (valid) {
      if (form.value.examId != undefined) {
        updateCyanExam(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addCyanExam(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const examIds = row.examId || ids.value
  proxy.$modal.confirm('是否确认删除考试编号为"' + examIds + '"的数据项？').then(function() {
    return delCyanExam(examIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  proxy.download('exam/exam/export', {
    ...queryParams.value
  }, `exam_${new Date().getTime()}.xlsx`)
}

// 学生分配
function handleAssignStudent() {
  if (ids.value.length !== 1) return
  listStudent({ status: '0' }).then(res => {
    allStudentList.value = res.rows.map(s => ({
      userId: s.userId,
      displayName: s.studentNo + ' - ' + (s.nickName || s.studentNo)
    }))
  })
  getStudentIds(ids.value[0]).then(res => {
    selectedStudentIds.value = res.data || []
  })
  studentOpen.value = true
}

function submitStudentAssign() {
  saveStudents(ids.value[0], selectedStudentIds.value).then(() => {
    proxy.$modal.msgSuccess('学生分配成功')
    studentOpen.value = false
  })
}

// 试题选择
function handleSelectQuestion() {
  if (ids.value.length !== 1) return
  // 重置筛选条件
  Object.assign(questionQuery, {
    category: undefined,
    difficulty: undefined,
    questionType: undefined
  })
  // 加载全部题目（根据筛选条件）
  loadQuestionList()
  // 获取当前考试已选择的题目ID
  getQuestionIds(ids.value[0]).then(res => {
    selectedQuestionIds.value = res.data || []
  })
  getQuestionPoints(ids.value[0]).then(res => {
    questionPoints.value = Object.fromEntries((res.data || []).map(q => [q.questionId, q.maxScore]))
  })
  questionOpen.value = true
}

function submitQuestionAssign() {
  const points = Object.fromEntries(selectedQuestionIds.value.map(id => [id, questionPoints.value[id] ?? 10]))
  saveQuestions(ids.value[0], selectedQuestionIds.value).then(() => {
    if (selectedQuestionIds.value.length) return saveQuestionPoints(ids.value[0], points)
  }).then(() => {
    proxy.$modal.msgSuccess('试题选择成功')
    questionOpen.value = false
  })
}

getList()
</script>

<style scoped>
.question-points { margin-top: 16px; max-height: 220px; overflow: auto; border-top: 1px solid #dce3e7; }
.question-point-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.question-point-row > span:first-child { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
