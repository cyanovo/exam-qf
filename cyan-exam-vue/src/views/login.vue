<template>
  <div class="login">
    <!-- 动态粒子背景画布（参考锁屏页实现） -->
    <canvas ref="particleCanvas" class="particle-bg"></canvas>

    <!-- 额外 CSS 光晕增强层次 -->
    <div class="glow-orb glow-orb--1"></div>
    <div class="glow-orb glow-orb--2"></div>

    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">{{ title }}</h3>
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          auto-complete="off"
          placeholder="账号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          auto-complete="off"
          placeholder="密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      <el-checkbox v-model="loginForm.rememberMe" style="margin:0px 0px 25px 0px;">记住密码</el-checkbox>
      <el-form-item style="width:100%;">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          style="width:100%;"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!--  底部  -->
    <div class="el-login-footer">
      <span>{{ footerContent }}</span>
    </div>
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

// ==================== 粒子背景系统（参考锁屏页） ====================
const particleCanvas = ref(null)
let animationId = null
let particles = []

const PARTICLE_COLORS = [
  'rgba(0,137,123,',    // 深青
  'rgba(38,166,154,',    // 中青
  'rgba(128,203,196,',   // 浅青
  'rgba(255,193,7,',     // 琥珀金
  'rgba(255,255,255,',   // 白
]

function initParticles() {
  const canvas = particleCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const resize = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  // 初始粒子：更多数量 + 更大尺寸变化
  particles = Array.from({ length: 120 }, () => {
    const colorIdx = Math.floor(Math.random() * PARTICLE_COLORS.length)
    return {
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      r: Math.random() * 3 + 1,                          // 1~4px
      dx: (Math.random() - 0.5) * 0.8,
      dy: (Math.random() - 0.5) * 0.8,
      alpha: Math.random() * 0.6 + 0.2,
      color: PARTICLE_COLORS[colorIdx],
      pulse: Math.random() * Math.PI * 2,                 // 闪烁相位
      pulseSpeed: Math.random() * 0.03 + 0.01,            // 闪烁速度
    }
  })

  const draw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    particles.forEach(p => {
      // 脉冲闪烁
      p.pulse += p.pulseSpeed
      const pulseAlpha = p.alpha * (0.7 + 0.3 * Math.sin(p.pulse))

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = p.color + pulseAlpha + ')'
      ctx.fill()

      // 较大粒子加光晕
      if (p.r > 2) {
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r * 2.5, 0, Math.PI * 2)
        ctx.fillStyle = p.color + (pulseAlpha * 0.12) + ')'
        ctx.fill()
      }

      p.x += p.dx
      p.y += p.dy

      if (p.x < -20 || p.x > canvas.width + 20) p.dx *= -1
      if (p.y < -20 || p.y > canvas.height + 20) p.dy *= -1
    })

    // 粒子间连线（距离 ≤ 130px）
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dist = Math.hypot(a.x - b.x, a.y - b.y)
        if (dist < 130) {
          const lineAlpha = 0.18 * (1 - dist / 130)
          // 连线颜色取两粒子平均
          ctx.beginPath()
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(255,255,255,${lineAlpha})`
          ctx.lineWidth = 0.6
          ctx.stroke()
        }
      }
    }

    animationId = requestAnimationFrame(draw)
  }
  draw()
}

onMounted(() => {
  nextTick(() => initParticles())
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
})

getCode()
getCookie()
</script>

<style lang='scss' scoped>
// ==================== 容器 & 背景渐变 ====================
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  position: relative;
  overflow: hidden;
  // 深色渐变基底（粒子在其上绘制）
  background: linear-gradient(
    145deg,
    #0a1a2e 0%,
    #0d2b35 20%,
    #132e3f 40%,
    #0f2238 60%,
    #0c1f2e 80%,
    #091420 100%
  );
}

.particle-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

// ==================== 大尺寸 CSS 光晕（增强层次感） ====================
.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
  pointer-events: none;
  animation: glowDrift 15s ease-in-out infinite;

  &--1 {
    width: 420px;
    height: 420px;
    background: rgba(0, 137, 123, 0.18);
    top: -15%;
    right: -10%;
    animation-delay: 0s;
    animation-duration: 16s;
  }
  &--2 {
    width: 340px;
    height: 340px;
    background: rgba(255, 193, 7, 0.09);
    bottom: -12%;
    left: -8%;
    animation-delay: -5s;
    animation-duration: 14s;
  }
}

@keyframes glowDrift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25%      { transform: translate(40px, -25px) scale(1.15); }
  50%      { transform: translate(-20px, 30px) scale(0.9); }
  75%      { transform: translate(-35px, -15px) scale(1.08); }
}

// ==================== 登录卡片（深色玻璃态，参考锁屏页） ====================
.login-form {
  position: relative;
  z-index: 2;
  width: 420px;
  padding: 44px 40px 28px;
  background: rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 4px 12px rgba(0, 0, 0, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow:
      0 24px 72px rgba(0, 0, 0, 0.55),
      0 6px 16px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.12);
  }

  .title {
    margin: 0 auto 36px;
    text-align: center;
    color: #ffffff;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 3px;
    position: relative;
    text-shadow: 0 0 30px rgba(0, 137, 123, 0.5);

    &::after {
      content: '';
      display: block;
      width: 50px;
      height: 3px;
      background: linear-gradient(90deg, #00897b, #ffc107, #4db6ac);
      border-radius: 2px;
      margin: 14px auto 0;
    }
  }

  // 表单项间距
  :deep(.el-form-item) {
    margin-bottom: 22px;
  }

  // 输入框 — 深色药丸风格（参考锁屏页）
  .el-input {
    height: 46px;
    :deep(.el-input__wrapper) {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 50px;
      box-shadow: none;
      padding: 0 18px;
      transition: all 0.3s ease;

      &:hover {
        border-color: rgba(255, 255, 255, 0.25);
        background: rgba(255, 255, 255, 0.11);
      }

      &.is-focus {
        border-color: rgba(0, 137, 123, 0.6);
        background: rgba(255, 255, 255, 0.12);
        box-shadow: 0 0 0 3px rgba(0, 137, 123, 0.15);
      }
    }
    :deep(input) {
      height: 46px;
      color: #fff;
      font-size: 14px;

      &::placeholder {
        color: rgba(255, 255, 255, 0.3);
      }
    }
    :deep(.el-input__prefix) {
      color: rgba(255, 255, 255, 0.4);
    }
  }

  .input-icon {
    height: 46px;
    width: 16px;
    color: rgba(255, 255, 255, 0.45);
  }
}

// 记住密码 — 白色文字
:deep(.el-checkbox) {
  margin: 2px 0 28px;
  color: rgba(255, 255, 255, 0.7);

  .el-checkbox__inner {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.25);
  }

  .el-checkbox__input.is-checked .el-checkbox__inner {
    background-color: #00897b;
    border-color: #00897b;
  }
}

// 登录按钮 — 多色渐变
:deep(.el-button--primary) {
  width: 100%;
  height: 48px;
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 5px;
  background: linear-gradient(135deg, #00897b 0%, #00695c 40%, #004d40 100%);
  border: none;
  box-shadow: 0 4px 20px rgba(0, 137, 123, 0.35);
  transition: all 0.3s ease;

  &:hover {
    background: linear-gradient(135deg, #009688 0%, #00796b 35%, #00897b 70%, #00695c 100%);
    box-shadow: 0 6px 28px rgba(0, 137, 123, 0.5), 0 0 0 2px rgba(255, 193, 7, 0.15);
    transform: translateY(-2px);
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 10px rgba(0, 137, 123, 0.3);
  }
}

// 注册链接
.link-type {
  color: rgba(255, 255, 255, 0.6);
  font-weight: 500;
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: #ffc107;
  }
}

// 验证码
.login-code {
  width: 33%;
  height: 46px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
    border-radius: 50px;
  }
}
.login-code-img {
  height: 46px;
  padding-left: 12px;
}

.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}

// ==================== 底部版权 ====================
.el-login-footer {
  height: 44px;
  line-height: 44px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  font-size: 13px;
  letter-spacing: 1px;
  z-index: 2;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  background: rgba(0, 0, 0, 0.2);
}
</style>
