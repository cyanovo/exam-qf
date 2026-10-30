<template>
  <main class="password-page">
    <el-form class="password-form" label-position="top" @submit.prevent="submit">
      <h1>修改初始密码</h1>
      <el-alert title="账号安全验证：完成密码修改后才能进入系统" type="warning" :closable="false" />
      <el-form-item label="当前密码"><el-input v-model="oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
      <el-form-item label="新密码（10至64位，含字母、数字及特殊字符）"><el-input v-model="newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-form-item label="确认新密码"><el-input v-model="confirm" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="busy">修改并进入系统</el-button>
      <el-button @click="logout">退出登录</el-button>
    </el-form>
  </main>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const router = useRouter(), store = useUserStore()
const oldPassword = ref(''), newPassword = ref(''), confirm = ref(''), busy = ref(false)
async function submit() {
  if (newPassword.value !== confirm.value) return ElMessage.error('两次新密码不一致')
  if (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,64}$/.test(newPassword.value)) return ElMessage.error('新密码不符合强度要求')
  busy.value = true
  try {
    await request.put('/system/user/profile/updatePwd', { oldPassword: oldPassword.value, newPassword: newPassword.value })
    await store.getInfo()
    ElMessage.success('密码已修改')
    await router.replace('/')
  } finally { busy.value = false }
}
async function logout() { await store.logOut(); await router.replace('/login') }
</script>
<style scoped>
.password-page { min-height: 100vh; display: grid; place-items: center; background: #f3f6f8; padding: 24px; }
.password-form { width: min(100%, 460px); }
h1 { font-size: 24px; margin-bottom: 24px; }
.el-alert { margin-bottom: 24px; }
</style>
