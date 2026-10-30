<template>
  <div class="login">
    <header class="login-header">
      <span class="brand-symbol"><svg-icon icon-class="education" /></span>
      <span class="brand-name">{{ title }}</span>
      <span class="brand-caption">考试系统</span>
    </header>
    <main class="login-main">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top">
      <div class="form-heading">
        <h1 class="title">欢迎登录</h1>
        <p class="form-subtitle">{{ title }} · 账号登录</p>
      </div>
      <el-form-item prop="username" label="账号">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          autocomplete="username"
          aria-label="账号"
          placeholder="请输入账号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password" label="密码">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          autocomplete="current-password"
          aria-label="密码"
          show-password
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" label="验证码" v-if="captchaEnabled" class="captcha-field">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          aria-label="验证码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <button type="button" class="captcha-refresh" aria-label="刷新验证码" title="刷新验证码" @click="getCode">
            <img :src="codeUrl" alt="验证码" class="login-code-img"/>
          </button>
        </div>
      </el-form-item>
      <div class="login-options">
        <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
        <router-link v-if="register" class="link-type" to="/register">立即注册</router-link>
      </div>
      <el-form-item class="login-action">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          style="width:100%;"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登录</span>
          <span v-else>登录中...</span>
        </el-button>
      </el-form-item>
    </el-form>
    </main>
    <!--  底部  -->
    <footer class="el-login-footer">
      <span>{{ footerContent }}</span>
    </footer>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100%;
  min-height: 100dvh;
  padding: 0 32px;
  box-sizing: border-box;
  background: #f7f8fa;
  color: #24292f;
  color-scheme: light;
  letter-spacing: 0;
  --el-text-color-primary: #24292f;
  --el-text-color-regular: #454c55;
  --el-color-primary: #2463ad;
  --el-color-danger: #bc3542;
  --el-text-color-placeholder: #77818d;
  --el-disabled-bg-color: #eef1f5;
}

.login-header {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 1280px;
  min-height: 88px;
  flex-shrink: 0;
}
.brand-symbol {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #fff;
  background: #2463ad;
  font-size: 22px;
}
.brand-name { font-size: 20px; font-weight: 600; }
.brand-caption {
  padding-left: 12px;
  border-left: 1px solid #d9dee5;
  color: #68717d;
  font-size: 13px;
}
.login-main {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  flex: 1;
  padding: 32px 0 64px;
}
.login-form {
  width: 432px;
  max-width: 100%;
  padding: 36px 40px 40px;
  background: #fff;
  border: 1px solid #e1e5eb;
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(32, 44, 62, 0.04);
  box-sizing: border-box;

  .form-heading { margin-bottom: 28px; }
  .form-subtitle { margin: 10px 0 0; color: #68717d; font-size: 14px; line-height: 1.6; }

  .title {
    margin: 0;
    color: #24292f;
    font-size: 24px;
    font-weight: 600;
    letter-spacing: 0;
    overflow-wrap: anywhere;
  }

  :deep(.el-form-item) {
    margin-bottom: 22px;
  }
  :deep(.el-form-item__label) {
    padding: 0;
    margin-bottom: 8px;
    line-height: 20px;
    color: #454c55;
    font-weight: 500;
  }
  :deep(.el-form-item.is-required .el-form-item__label::before) { display: none; }
  :deep(.el-form-item__error) { padding-top: 5px; font-size: 12px; }
  :deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px #bc3542 inset; }
  :deep(.el-input__password) { color: #68717d; }

  .el-input {
    height: 44px;
    :deep(.el-input__wrapper) {
      background: #fff;
      border-radius: 6px;
      box-shadow: 0 0 0 1px #d7dde5 inset;
      padding: 0 12px;
      &:hover { box-shadow: 0 0 0 1px #8994a2 inset; }
      &.is-focus {
        box-shadow: 0 0 0 1px #2463ad inset, 0 0 0 3px #eaf1fa;
      }
    }
    :deep(input) {
      height: 44px;
      color: #24292f;
      font-size: 14px;
      &::placeholder { color: #68717d; }
    }
  }

  .input-icon {
    height: 44px;
    width: 16px;
    color: #68717d;
  }
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 0 20px;
  min-height: 32px;
}
.login-form :deep(.login-action) { margin-bottom: 0; }
:deep(.el-checkbox) {
  color: #454c55;
  .el-checkbox__inner {
    background: #fff;
    border-color: #8994a2;
  }
  .el-checkbox__input.is-checked .el-checkbox__inner {
    background-color: #2463ad;
    border-color: #2463ad;
  }
}

:deep(.el-button--primary) {
  width: 100%;
  height: 46px;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0;
  background: #2463ad;
  color: #fff;
  border: none;
  box-shadow: none;
  &:hover { background: #1e5290; }
  &:focus-visible { outline: 3px solid #b9d2ef; outline-offset: 3px; }
}

.link-type { color: #2463ad; }

.captcha-field :deep(.el-form-item__content) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  gap: 12px;
}
.login-code {
  width: 100%;
  height: 44px;
}
.captcha-refresh {
  display: block;
  width: 100%;
  height: 44px;
  border: 1px solid #c9ced6;
  border-radius: 6px;
  background: #fff;
  padding: 0;
  cursor: pointer;
  &:focus-visible { outline: 2px solid #2463ad; outline-offset: 2px; }
}
.login-code-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.el-login-footer {
  width: 100%;
  max-width: 1280px;
  border-top: 1px solid #e5e8ed;
  padding: 20px 0;
  text-align: center;
  color: #68717d;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

@media (max-width: 480px) {
  .login { padding: 0 20px; }
  .login-header { min-height: 76px; }
  .login-main { padding: 20px 0 36px; }
  .login-form { padding: 28px 24px 30px; }
  .captcha-field :deep(.el-form-item__content) { grid-template-columns: minmax(0, 1fr) 96px; gap: 8px; }
}
</style>
