<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="学号" prop="studentNo">
        <el-input v-model="queryParams.studentNo" placeholder="请输入学号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="姓名" prop="nickName">
        <el-input v-model="queryParams.nickName" placeholder="请输入姓名" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="班级" prop="className">
        <el-input v-model="queryParams.className" placeholder="请输入班级" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="账号状态" prop="status">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['system:student:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['system:student:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['system:student:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['system:student:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['system:student:import']">一键创建</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="studentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="学号" align="center" prop="studentNo" />
      <el-table-column label="姓名" align="center" prop="nickName" show-overflow-tooltip />
      <el-table-column label="班级" align="center" prop="className" />
      <el-table-column label="年级" align="center" prop="grade" />
      <el-table-column label="账号状态" align="center" prop="status">
        <template #default="scope">
          <el-switch v-model="scope.row.status" :active-value="'0'" :inactive-value="'1'" disabled />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:student:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:student:remove']">删除</el-button>
          <el-button link type="primary" @click="handleResetPwd(scope.row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="studentRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号（也是登录账号）" />
        </el-form-item>
        <el-form-item label="姓名" prop="nickName">
          <el-input v-model="form.nickName" placeholder="请输入学生姓名" />
        </el-form-item>
        <el-form-item label="班级" prop="className">
          <el-input v-model="form.className" placeholder="请输入班级" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="form.grade" placeholder="请输入年级" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 一键创建对话框 -->
    <el-dialog title="一键创建学生账号" v-model="importOpen" width="550px" append-to-body>
      <el-form ref="importRef" :model="importForm" :rules="importRules" label-width="100px">
        <el-form-item label="账号前缀" prop="prefix">
          <el-input v-model="importForm.prefix" placeholder="例如 STU" />
        </el-form-item>
        <el-form-item label="起始编号" prop="startNo">
          <el-input-number v-model="importForm.startNo" :min="1" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="创建数量" prop="count">
          <el-input-number v-model="importForm.count" :min="1" :max="200" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="默认密码" prop="password">
          <el-input v-model="importForm.password" show-password placeholder="建议设置统一密码" />
        </el-form-item>
        <el-form-item label="班级" prop="className">
          <el-input v-model="importForm.className" placeholder="例如：软件2301" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="importForm.grade" placeholder="例如：2024级" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitImport">开始创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Student">
import { listStudent, getStudent, addStudent, updateStudent, delStudent, importStudents } from "@/api/system/student"
import { getCurrentInstance, reactive, ref, toRefs } from "vue"
import request from '@/utils/request'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

const studentList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const importOpen = ref(false)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    studentNo: undefined,
    nickName: undefined,
    className: undefined,
    status: undefined
  },
  rules: {
    studentNo: [{ required: true, message: "学号不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

// 一键创建表单
const importForm = reactive({
  prefix: "STU",
  startNo: 1,
  count: 10,
  password: "123456",
  className: "",
  grade: ""
})

const importRules = reactive({
  prefix: [{ required: true, message: "账号前缀不能为空", trigger: "blur" }],
  startNo: [{ required: true, message: "起始编号不能为空", trigger: "blur" }],
  count: [{ required: true, message: "创建数量不能为空", trigger: "blur" }],
  password: [{ required: true, message: "默认密码不能为空", trigger: "blur" }]
})

/** 查询列表 */
function getList() {
  loading.value = true
  listStudent(queryParams.value).then(response => {
    studentList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    studentId: undefined,
    studentNo: "",
    nickName: "",
    className: "",
    grade: "",
    remark: ""
  }
  proxy.resetForm("studentRef")
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.studentId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加学生"
}

/** 修改 */
function handleUpdate(row) {
  reset()
  const studentId = row.studentId || ids.value
  getStudent(studentId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改学生"
  })
}

/** 提交 */
function submitForm() {
  proxy.$refs["studentRef"].validate(valid => {
    if (valid) {
      if (form.value.studentId != undefined) {
        updateStudent(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addStudent(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除 */
function handleDelete(row) {
  const studentIds = row.studentId || ids.value
  proxy.$modal.confirm('是否确认删除学号为"' + studentIds + '"的数据项？').then(function() {
    return delStudent(studentIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出（复用后端导出接口） */
function handleExport() {
  proxy.download('system/student/export', {
    ...queryParams.value
  }, `student_${new Date().getTime()}.xlsx`)
}

/** 一键创建 */
function handleImport() {
  // 重置表单
  Object.assign(importForm, {
    prefix: "STU",
    startNo: 1,
    count: 10,
    password: "123456",
    className: "",
    grade: ""
  })
  importOpen.value = true
}

/** 提交一键创建 */
function submitImport() {
  proxy.$refs["importRef"].validate(valid => {
    if (valid) {
      importStudents(importForm).then(res => {
        if (res.code === 200) {
          proxy.$modal.msgSuccess(res.msg || "创建成功")
          importOpen.value = false
          getList()
        } else {
          proxy.$modal.msgError(res.msg)
        }
      })
    }
  })
}

/** 重置密码 */
function handleResetPwd(row) {
  proxy.$prompt('请输入新密码', '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password'
  }).then(({ value }) => {
    return request({
      url: '/system/user/resetPwd',
      method: 'put',
      data: {
        userId: row.userId,
        password: value      // 传入明文，后端会自动加密
      }
    })
  }).then(() => {
    proxy.$modal.msgSuccess("密码重置成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.app-container {
  padding: 20px;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background-color: #e0f2f1 !important;
  color: #00695c !important;
  font-weight: 600;
}

:deep(.el-button--primary) {
  border-radius: 10px;
}

:deep(.el-button) {
  border-radius: 10px;
}

:deep(.el-dialog) {
  border-radius: 20px;
}
</style>