<template>
  <div class="app-container">
    <el-collapse class="exam-guide">
      <el-collapse-item title="操作指引：准备考试、发布与删除" name="guide">
        <ol>
          <li>新增考试，设置名称和起止时间。</li>
          <li>在该行分配学生、选择题目；机器人实操题、评分步骤和章节顺序在“编排与场次”设置。</li>
          <li>确认名单和题目后发布，学生才能进入考试。发布后需要修改，请在开考前进入“编排与场次”撤回发布。</li>
          <li>不需要的草稿可直接删除，过期草稿也可以。已发布但未开考的考试，点击删除后会自动撤回，无需另填原因。</li>
          <li>已结束且全员无实际作答的白卷考试可直接删除，不用先评分。已有实际答案、代码、人工评分、复核或材料记录的考试需保留并归档。</li>
        </ol>
      </el-collapse-item>
    </el-collapse>
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
        <el-tooltip :content="selectionHint"><span><el-button type="success" plain icon="Edit" :disabled="!editableSelection" @click="handleUpdate" v-hasPermi="['exam:exam:edit']">修改</el-button></span></el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip :content="deleteHint"><span><el-button type="danger" plain icon="Delete" :disabled="!selectedRows.length" @click="handleDelete" v-hasPermi="['exam:exam:remove']">删除</el-button></span></el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['exam:exam:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip :content="selectionHint"><span><el-button type="primary" plain icon="User" @click="handleAssignStudent" :disabled="!editableSelection"
          v-hasPermi="['exam:exam:edit']">分配学生</el-button></span></el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip :content="selectionHint"><span><el-button type="success" plain icon="List" @click="handleSelectQuestion" :disabled="!editableSelection"
          v-hasPermi="['exam:exam:edit']">选择题目</el-button></span></el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip :content="selectionHint"><span><el-button type="primary" plain icon="CircleCheck" :disabled="!editableSelection" @click="handlePublish" v-hasPermi="['exam:exam:edit']">发布</el-button></span></el-tooltip>
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
        <template #default="{ row }">{{ row.lifecycle==='ARCHIVED' ? '已归档' : row.lifecycle==='TERMINATED' ? '已终止' : row.publishedAt ? "已发布" : canEdit(row) ? "草稿" : '草稿已过期' }}</template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="430">
        <template #default="scope">
          <el-button link type="primary" @click="$router.push(`/exam/manage/${scope.row.examId}`)" v-hasPermi="['exam:exam:edit']">编排与场次</el-button>
          <template v-if="scope.row.publishedAt">
            <el-button link type="primary" @click="$router.push(`/exam/grading/workspace/${scope.row.examId}`)" v-hasPermi="['exam:grading:list']">评分与成绩</el-button>
          </template>
          <el-button v-if="canEdit(scope.row)" link type="primary" @click="handleAssignStudent(scope.row)" v-hasPermi="['exam:exam:edit']">分配学生</el-button>
          <el-button v-if="canEdit(scope.row)" link type="primary" @click="handleSelectQuestion(scope.row)" v-hasPermi="['exam:exam:edit']">选择题目</el-button>
          <el-button v-if="canEdit(scope.row)" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['exam:exam:edit']">修改</el-button>
          <el-button v-if="canEdit(scope.row)" link type="primary" icon="CircleCheck" @click="handlePublish(scope.row)" v-hasPermi="['exam:exam:edit']">发布</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['exam:exam:remove']">删除</el-button>
          <el-button v-if="isSuperAdmin" link type="danger" icon="Delete" @click="openForceDelete(scope.row)"
            v-hasPermi="['exam:exam:remove']">强制删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="删除考试" v-model="deleteOpen" width="620px" append-to-body
      :close-on-click-modal="false" :close-on-press-escape="!deleting" :show-close="!deleting">
      <template v-if="deleteCandidates.length">
        <p class="delete-summary">将删除以下 {{ deleteCandidates.length }} 场考试，名单、选题和实操题配置一并移除，题库和学生账号不受影响。删除后无法恢复。</p>
        <ul class="delete-list">
          <li v-for="exam in deleteCandidates" :key="exam.examId">
            <strong>{{ exam.examName }}</strong>
            <span>{{ exam.blankDeletion ? '仅全员白卷可删除' : exam.publishedAt ? '自动撤回发布后删除' : '直接删除草稿' }}</span>
          </li>
        </ul>
        <p v-if="deleteCandidates.some(exam => exam.blankDeletion)" class="delete-summary">无需先评分。系统会检查全部考生，只有无实际答案、代码、人工评分、成绩复核或材料记录时才会删除；空白答卷和系统自动记零记录一并清理。</p>
        <p v-if="deleteCandidates.some(exam => exam.publishedAt && !exam.blankDeletion)" class="delete-summary">系统先撤回发布，再删除；若后续删除失败，已撤回的考试将保留为草稿。</p>
      </template>
      <div v-if="deleteBlocked.length" class="delete-blocked">
        <p>以下考试将保留，不会删除：</p>
        <div v-for="exam in deleteBlocked" :key="exam.examId" class="delete-blocked-row">
          <strong>{{ exam.examName }}</strong>
          <p>{{ exam.deleteReason }}</p>
          <el-button link type="primary" :disabled="deleting" @click="openExamManagement(exam.examId)"
            v-hasPermi="['exam:exam:edit']">前往编排与场次</el-button>
        </div>
      </div>
      <el-alert v-if="deleteError" :title="deleteError" type="error" :closable="false" show-icon />
      <p v-if="deleteError" class="delete-summary">请返回列表查看最新状态。若撤回成功但删除被拦截，考试会保留为草稿，不会恢复为已发布；已有作答、评分或材料不会被强制清除。</p>
      <template #footer>
        <el-button :disabled="deleting" @click="deleteOpen = false">{{ deleteError || !deleteCandidates.length ? '返回列表' : '取消' }}</el-button>
        <el-button v-if="deleteCandidates.length" type="danger" :loading="deleting" :disabled="!!deleteError"
          @click="confirmDelete">确认删除 {{ deleteCandidates.length }} 场</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="forceDeleteOpen" title="强制删除考试" width="min(520px, 96vw)" append-to-body
      :close-on-click-modal="false" :close-on-press-escape="!forceDeleting" :show-close="!forceDeleting">
      <p>将永久删除“{{ forceTarget?.examName }}”及其考生名单、答卷、代码、评分、成绩、异常记录和材料。题库与学生账号不受影响。此操作无法恢复。</p>
      <el-form label-position="top">
        <el-form-item label="输入考试全名确认">
          <el-input v-model="forceName" autocomplete="off" :disabled="forceDeleting" />
        </el-form-item>
        <el-form-item label="删除原因（至少5字）">
          <el-input v-model="forceReason" type="textarea" :rows="3" maxlength="500"
            :disabled="forceDeleting" />
        </el-form-item>
      </el-form>
      <el-alert v-if="forceError" type="error" :title="forceError" :closable="false" show-icon />
      <template #footer>
        <el-button :disabled="forceDeleting" @click="forceDeleteOpen = false">取消</el-button>
        <el-button type="danger" :loading="forceDeleting" :disabled="!forceReady" @click="confirmForceDelete">永久删除</el-button>
      </template>
    </el-dialog>

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="min(600px, 96vw)" append-to-body>
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
    <StudentRosterDialog v-model="studentOpen" :exam-id="assignmentExamId" @saved="getList" />

    <!-- 选择题目对话框（带筛选） -->
    <el-dialog title="选择题目" v-model="questionOpen" width="min(900px, 96vw)" append-to-body :before-close="closeQuestionDialog" :close-on-click-modal="!questionSaving">
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
          <el-select v-model="questionQuery.questionType" placeholder="请选择" clearable @change="loadQuestionList" :disabled="questionSaving">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadQuestionList">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-transfer
        v-model="activeQuestionIds"
        :data="allQuestionList"
        :titles="['可选题目', '本次选题']"
        filterable
        filter-placeholder="请输入题干关键词"
        :props="{ key: 'questionId', label: 'title' }"
        style="margin-top: 10px"
      />
      <template v-if="robotSelection || selectedRobotQuestionIds.length || assignedRobotTasks.length">
        <el-form class="robot-assignment" label-position="top" :disabled="questionSaving">
          <el-form-item label="机器人运行观察时长（分钟）">
            <el-input-number v-model="robotDurationMinutes" :min="1" :max="240" aria-label="机器人运行观察时长（分钟）" />
          </el-form-item>
        </el-form>
        <section v-if="assignedRobotTasks.length" class="assigned-robot-tasks">
          <h3>已加入的机器人实操题</h3>
          <el-table :data="assignedRobotTasks">
            <el-table-column prop="title" label="题目" min-width="200" />
            <el-table-column prop="maxScore" label="满分" width="80" />
          </el-table>
        </section>
      </template>
      <div v-if="selectedQuestionIds.length" class="question-points">
        <div v-for="questionId in selectedQuestionIds" :key="questionId" class="question-point-row">
          <span>{{ allQuestionList.find(q => q.questionId === questionId)?.title || `题目 ${questionId}` }}</span>
          <el-input-number v-model="questionPoints[questionId]" :min="1" :max="100" :step="1" step-strictly size="small" aria-label="题目满分" />
          <span>分</span>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="questionSaving" @click="questionOpen = false">取 消</el-button>
        <el-button type="primary" :loading="questionSaving" @click="submitQuestionAssign">保存全部选题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CyanExam">
import {
  listCyanExam, getCyanExam, addCyanExam, updateCyanExam, delCyanExam,
  getQuestionIds, saveQuestions, publishCyanExam,
  getQuestionPoints, saveQuestionPoints
} from "@/api/system/info"
import StudentRosterDialog from "@/views/exam/manage/StudentRosterDialog.vue"
import request from "@/utils/request"
import auth from "@/plugins/auth"
import useUserStore from "@/store/modules/user"
import { computed, getCurrentInstance, reactive, ref, toRefs } from "vue"

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const isSuperAdmin = computed(() => Number(userStore.id) === 1)
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

const examList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const canEdit = row => (!row.lifecycle || row.lifecycle==='ACTIVE') && !row.publishedAt && row.startTime && new Date(row.startTime.replace(' ', 'T')).getTime() > Date.now()
const selectedRows = computed(() => examList.value.filter(row => ids.value.includes(row.examId)))
const editableSelection = computed(() => selectedRows.value.length === 1 && canEdit(selectedRows.value[0]))
const selectionHint = computed(()=> selectedRows.value.length!==1 ? '请先勾选一场考试，或使用该行的操作按钮' : editableSelection.value ? '操作已选考试' : selectedRows.value[0].publishedAt ? '已发布考试不能修改名单或题目；开考前可到编排与场次撤回发布' : '场次已过期、终止或归档，不能修改')
const deleteHint = computed(()=>!selectedRows.value.length ? '勾选考试后批量删除，也可直接点击该行的删除' : '查看待删除考试；不能删除的场次会单独列出并保留')
const deleteOpen = ref(false)
const deleteCandidates = ref([])
const deleteBlocked = ref([])
const deleting = ref(false)
const deleteError = ref('')
const forceDeleteOpen = ref(false)
const forceTarget = ref(null)
const forceName = ref('')
const forceReason = ref('')
const forceError = ref('')
const forceDeleting = ref(false)
const forceReady = computed(() => !forceDeleting.value && forceName.value === forceTarget.value?.examName
  && forceReason.value.trim().length >= 5)
const title = ref("")

// 学生分配相关
const studentOpen = ref(false)
const assignmentExamId = ref(null)

// 题目选择相关
const questionOpen = ref(false)
const allQuestionList = ref([])
const questionOptions = ref([])
const selectedQuestionIds = ref([])
const questionPoints = ref({})
const questionSaving = ref(false)
const robotQuestions = ref([])
const selectedRobotQuestionIds = ref([])
const robotDurationMinutes = ref(10)
const assignedRobotTasks = ref([])
const robotSelection = computed(() => questionQuery.questionType === 'ROBOT_PRACTICAL')
const activeQuestionIds = computed({
  get: () => [...selectedQuestionIds.value, ...selectedRobotQuestionIds.value.map(id => `ROBOT-${id}`)],
  set: value => {
    selectedRobotQuestionIds.value = value.filter(id => String(id).startsWith('ROBOT-')).map(id => Number(id.slice(6)))
    selectedQuestionIds.value = value.filter(id => !String(id).startsWith('ROBOT-'))
  }
})

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
  { label: "简答题", value: "SHORT_ANSWER" },
  { label: "Python编程", value: "PYTHON" },
  { label: "机器人实操编程", value: "ROBOT_PRACTICAL" }
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
    const options = [...questionOptions.value, ...robotQuestions.value.map(q => ({
      ...q, questionId: `ROBOT-${q.questionId}`, questionType: 'ROBOT_PRACTICAL'
    }))]
    allQuestionList.value = options.filter(q=>activeQuestionIds.value.includes(q.questionId) || (
      (!questionQuery.category || q.category===questionQuery.category) &&
      (!questionQuery.difficulty || q.difficulty===questionQuery.difficulty) &&
      (!questionQuery.questionType || q.questionType===questionQuery.questionType)
    )).map(q => ({
      questionId: q.questionId,
      title: `${q.title} (${typeOptions.find(type => type.value === q.questionType)?.label || q.questionType})`,
      disabled: questionSaving.value
    }))
}

// 查询考试列表
function getList() {
  loading.value = true
  return listCyanExam(queryParams.value).then(response => {
    examList.value = response.rows
    total.value = response.total
  }).finally(()=>{loading.value=false})
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
  if (deleting.value) return
  const rows = row?.examId ? [row] : selectedRows.value
  deleteCandidates.value = []
  deleteBlocked.value = []
  deleteError.value = ''
  for (const exam of rows) {
    const blankDeletion = !!exam.publishedAt && (exam.lifecycle === 'TERMINATED'
      || ((!exam.lifecycle || exam.lifecycle === 'ACTIVE') && new Date(exam.endTime?.replace(' ', 'T')).getTime() <= Date.now()))
    let reason = ''
    if (exam.lifecycle === 'ARCHIVED') reason = '已归档，需保留历史成绩和记录。'
    else if (blankDeletion) { /* The server checks all student records before deleting. */ }
    else if (exam.lifecycle && exam.lifecycle !== 'ACTIVE') reason = '当前状态不能删除。'
    else if (exam.publishedAt && !(new Date(exam.startTime?.replace(' ', 'T')).getTime() > Date.now())) {
      reason = '已发布且已到开考时间，不能直接删除。进行中的考试可在“编排与场次”终止，结束后完成成绩流程并归档。'
    } else if (exam.publishedAt && !auth.hasPermi('exam:exam:edit')) {
      reason = '已发布。请有考试编辑权限的管理员在开考前撤回，再删除。'
    }
    if (reason) deleteBlocked.value.push({ ...exam, deleteReason: reason })
    else deleteCandidates.value.push({ ...exam, blankDeletion })
  }
  deleteOpen.value = true
}

function openForceDelete(row) {
  forceTarget.value = row
  forceName.value = ''
  forceReason.value = ''
  forceError.value = ''
  forceDeleteOpen.value = true
}

async function confirmForceDelete() {
  if (!isSuperAdmin.value || !forceReady.value) return
  forceDeleting.value = true
  forceError.value = ''
  try {
    await request.delete(`/exam/manage/${forceTarget.value.examId}/force`, {
      data: { examName: forceName.value, reason: forceReason.value.trim() }
    })
    forceDeleteOpen.value = false
    proxy.$modal.msgSuccess('考试已强制删除')
    if (queryParams.value.pageNum > 1 && examList.value.length === 1) queryParams.value.pageNum--
    await getList()
  } catch (error) {
    forceError.value = error?.message || '删除失败，请刷新列表确认考试状态'
  } finally {
    forceDeleting.value = false
  }
}

function openExamManagement(id) {
  deleteOpen.value = false
  proxy.$router.push(`/exam/manage/${id}`)
}

async function confirmDelete() {
  if (deleting.value || deleteError.value || !deleteCandidates.value.length) return
  deleting.value = true
  let currentName = ''
  try {
    // Reuse the protected withdrawal API; deletion still validates all business records.
    for (const exam of deleteCandidates.value) {
      currentName = exam.examName
      if (exam.publishedAt && !exam.blankDeletion) {
        await request.post(`/exam/manage/${exam.examId}/withdraw`, { reason: '管理员确认删除考试，自动撤回发布' })
        exam.publishedAt = null
      }
    }
    currentName = ''
    await delCyanExam(deleteCandidates.value.map(exam => exam.examId))
    deleteOpen.value = false
    ids.value = []
    if (queryParams.value.pageNum > 1 && deleteCandidates.value.length === examList.value.length) {
      queryParams.value.pageNum--
    }
    proxy.$modal.msgSuccess(`已删除 ${deleteCandidates.value.length} 场考试`)
  } catch (error) {
    deleteError.value = `${currentName ? `“${currentName}”：` : ''}${error?.message || '操作未完成，请检查连接并返回列表确认状态'}`
  } finally {
    deleting.value = false
    getList().catch(() => proxy.$modal.msgWarning('列表刷新失败，请点击搜索重新加载'))
  }
}

function handleExport() {
  proxy.download('exam/exam/export', {
    ...queryParams.value
  }, `exam_${new Date().getTime()}.xlsx`)
}

// 学生分配
function handleAssignStudent(row) {
  assignmentExamId.value=row?.examId || ids.value[0]
  if (!assignmentExamId.value) return
  studentOpen.value = true
}

// 试题选择
async function handleSelectQuestion(row) {
  assignmentExamId.value=row?.examId || ids.value[0]
  if (!assignmentExamId.value) return
  // 重置筛选条件
  Object.assign(questionQuery, {
    category: undefined,
    difficulty: undefined,
    questionType: undefined
  })
  const [options, assigned, points, robots, onsite]=await Promise.all([
    request.get(`/exam/manage/${assignmentExamId.value}/questions`),
    getQuestionIds(assignmentExamId.value),getQuestionPoints(assignmentExamId.value),
    request.get(`/exam/manage/${assignmentExamId.value}/robot-questions`),
    request.get(`/exam/onsite/${assignmentExamId.value}`)
  ])
  questionOptions.value=options.data
  robotQuestions.value=robots.data || []
  assignedRobotTasks.value=onsite.data.tasks || []
  selectedRobotQuestionIds.value=[]
  robotDurationMinutes.value=10
  selectedQuestionIds.value=assigned.data || []
  questionPoints.value=Object.fromEntries((points.data || []).map(q=>[q.questionId,q.maxScore]))
  loadQuestionList()
  questionOpen.value = true
}

function closeQuestionDialog(done) {
  if (!questionSaving.value) done()
}

async function submitQuestionAssign() {
  if (questionSaving.value) return
  questionSaving.value = true
  loadQuestionList()
  try {
    const points = Object.fromEntries(selectedQuestionIds.value.map(id => [id, questionPoints.value[id] ?? 10]))
    await saveQuestions(assignmentExamId.value, selectedQuestionIds.value)
    if (selectedQuestionIds.value.length) await saveQuestionPoints(assignmentExamId.value, points)
    // Remove each successful addition from the pending set so retrying cannot duplicate it.
    for (const questionId of [...selectedRobotQuestionIds.value]) {
      const response = await request.post(`/exam/onsite/${assignmentExamId.value}/tasks/from-bank`, {
        questionId, durationMinutes: robotDurationMinutes.value
      })
      assignedRobotTasks.value.push({
        id: response.data, title: robotQuestions.value.find(q => q.questionId === questionId)?.title
      })
      selectedRobotQuestionIds.value = selectedRobotQuestionIds.value.filter(id => id !== questionId)
    }
    proxy.$modal.msgSuccess('全部选题已保存')
    questionOpen.value = false
  } finally {
    questionSaving.value = false
    loadQuestionList()
  }
}

getList()
</script>

<style scoped>
.exam-guide { margin-bottom: 20px; }
.exam-guide ol { margin: 0; padding-left: 24px; line-height: 2; color: var(--el-text-color-regular); }
.delete-summary { margin-top: 0; line-height: 1.7; color: var(--el-text-color-regular); }
.delete-list { padding: 0; list-style: none; max-height: 240px; overflow: auto; }
.delete-list li { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.delete-list strong { overflow-wrap: anywhere; min-width: 0; }
.delete-list span { flex-shrink: 0; color: var(--el-text-color-secondary); }
.delete-blocked { margin: 16px 0; }
.delete-blocked-row { padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
.delete-blocked-row p { margin: 6px 0; line-height: 1.6; }
.question-points { margin-top: 16px; max-height: 220px; overflow: auto; border-top: 1px solid #dce3e7; }
.question-point-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.question-point-row > span:first-child { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.robot-assignment { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 20px; }
.robot-assignment .el-select { width: 240px; max-width: 100%; }
.assigned-robot-tasks h3 { font-size: 16px; }
</style>
