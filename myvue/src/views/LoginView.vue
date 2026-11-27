<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decorations" aria-hidden="true">
      <div class="bg-blob blob-1"></div>
      <div class="bg-blob blob-2"></div>
      <div class="bg-blob blob-3"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 粒子背景 -->
    <canvas class="particles-canvas" ref="particlesCanvas" aria-hidden="true"></canvas>

    <div class="login-container">
      <!-- 左侧品牌区 -->
      <div class="brand-panel">
        <div class="brand-content">
          <div class="logo-wrapper">
            <div class="logo-icon">
              <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="64" height="64" rx="16" fill="currentColor" />
                <path d="M18 22h28M18 32h20M18 42h16" stroke="white" stroke-width="3" stroke-linecap="round" />
              </svg>
            </div>
          </div>
          <h1 class="brand-title">景区管理系统</h1>
          <p class="brand-subtitle">专业的智慧景区票务与运营管理平台</p>

          <div class="brand-features">
            <div class="feature-item" v-for="(feature, index) in features" :key="index" :style="{ transitionDelay: `${index * 100}ms` }">
              <div class="feature-icon" :class="feature.iconClass">
                <component :is="feature.icon" />
              </div>
              <span class="feature-text">{{ feature.text }}</span>
            </div>
          </div>

          <div class="brand-footer">
            <span class="version">v2.0.0</span>
            <span class="copyright">© 2024 Scenic Management</span>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单区 -->
      <div class="form-panel">
        <div class="form-container">
          <div class="form-header">
            <h2 class="form-title">欢迎回来</h2>
            <p class="form-subtitle">登录您的账号以继续</p>
          </div>

          <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="login-form" label-position="top">
            <el-form-item prop="username" class="form-field">
              <label class="field-label">用户名</label>
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                :prefix-icon="User"
                clearable
                autocomplete="username"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item prop="password" class="form-field">
              <div class="field-header">
                <label class="field-label">密码</label>
                <a href="javascript:void(0)" class="forgot-link" @click="handleForgotPassword">忘记密码？</a>
              </div>
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
                autocomplete="current-password"
              />
            </el-form-item>

            <el-form-item prop="remember" class="form-field checkbox-field">
              <el-checkbox v-model="loginForm.remember" class="remember-checkbox">
                记住我
              </el-checkbox>
            </el-form-item>

            <el-form-item class="form-field submit-field">
              <el-button
                type="primary"
                @click="handleLogin"
                size="large"
                class="submit-btn"
                :loading="loading"
                :disabled="loading"
              >
                <template #default>
                  <span v-if="!loading">登 录</span>
                  <span v-else>登录中...</span>
                </template>
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <div class="demo-accounts">
              <span class="demo-label">演示账号：</span>
              <el-button-group>
                <el-button link size="small" @click="fillAccount('admin', '123456')">admin</el-button>
                <el-button link size="small" @click="fillAccount('testuser', '123456')">testuser</el-button>
              </el-button-group>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Tickets, Monitor, UserFilled } from '@element-plus/icons-vue'
import { login } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref(null)
const particlesCanvas = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: true
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度 2-20 字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const features = [
  { icon: Tickets, iconClass: 'icon-tickets', text: '智能票务管理' },
  { icon: Monitor, iconClass: 'icon-monitor', text: '实时数据大屏' },
  { icon: UserFilled, iconClass: 'icon-user', text: '全维度用户运营' }
]

// 粒子动画
let animationId = null
// 清理函数存在模块作用域的变量里，而不是挂在 canvas DOM 上。
// 之前的写法 particlesCanvas.value._cleanup 在 onUnmounted 时
// particlesCanvas.value 已经是 null，直接抛
// "Cannot read properties of null (reading '_cleanup')"，
// 导致组件卸载钩子报错（登录成功跳转时必然触发）。
let particlesCleanup = null

const initParticles = () => {
  const canvas = particlesCanvas.value
  if (!canvas) return null

  const ctx = canvas.getContext('2d')
  const particles = []
  const particleCount = 30

  const resize = () => {
    canvas.width = canvas.offsetWidth
    canvas.height = canvas.offsetHeight
  }

  class Particle {
    constructor() {
      this.reset()
    }
    reset() {
      this.x = Math.random() * canvas.width
      this.y = Math.random() * canvas.height
      this.vx = (Math.random() - 0.5) * 0.3
      this.vy = (Math.random() - 0.5) * 0.3
      this.radius = Math.random() * 1.5 + 0.5
      this.opacity = Math.random() * 0.3 + 0.1
    }
    update() {
      this.x += this.vx
      this.y += this.vy
      if (this.x < 0 || this.x > canvas.width || this.y < 0 || this.y > canvas.height) {
        this.reset()
      }
    }
    draw() {
      ctx.beginPath()
      ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(37, 99, 235, ${this.opacity})`
      ctx.fill()
    }
  }

  for (let i = 0; i < particleCount; i++) {
    particles.push(new Particle())
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    // 绘制连线
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < 120) {
          ctx.beginPath()
          ctx.moveTo(particles[i].x, particles[i].y)
          ctx.lineTo(particles[j].x, particles[j].y)
          ctx.strokeStyle = `rgba(37, 99, 235, ${0.1 * (1 - dist / 120)})`
          ctx.lineWidth = 0.5
          ctx.stroke()
        }
      }
    }

    particles.forEach(p => {
      p.update()
      p.draw()
    })

    animationId = requestAnimationFrame(animate)
  }

  resize()
  animate()

  window.addEventListener('resize', resize)
  return () => window.removeEventListener('resize', resize)
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  const valid = await loginFormRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    const res = await login(loginForm)
    if (res.code === 200) {
      userStore.setLoginData(res.data, res.data.token, res.data.refreshToken)
      await nextTick()
      await router.push('/projects')
      ElMessage.success('登录成功')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '登录失败，请检查后端是否启动')
  } finally {
    loading.value = false
  }
}

const handleForgotPassword = () => {
  ElMessage.info('请联系管理员重置密码')
}

const fillAccount = (username, password) => {
  loginForm.username = username
  loginForm.password = password
}

onMounted(() => {
  particlesCleanup = initParticles()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
    animationId = null
  }
  if (typeof particlesCleanup === 'function') {
    particlesCleanup()
    particlesCleanup = null
  }
})
</script>

<style scoped>
/* ============================================================
   登录页
   设计思路：浅色页面 + 左侧深色品牌面板，形成清晰的明暗对比。
   旧版把整页背景也做成深色，导致容器与背景糊在一起、层次不清；
   现在页面是浅灰，深色只保留在品牌面板里，视觉重心明确。
   ============================================================ */

.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  background: var(--slate-100);
  overflow: hidden;
}

/* ---------- 背景：极克制的网格 + 柔光 ---------- */
.bg-decorations {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(37, 99, 235, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(37, 99, 235, 0.045) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, #000 40%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, #000 40%, transparent 100%);
}

.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  animation: blobFloat 24s ease-in-out infinite;
}

.blob-1 {
  width: 420px;
  height: 420px;
  top: -140px;
  right: -80px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.45), transparent 70%);
}

.blob-2 {
  width: 360px;
  height: 360px;
  bottom: -120px;
  left: -100px;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.35), transparent 70%);
  animation-delay: -8s;
}

.blob-3 {
  width: 280px;
  height: 280px;
  top: 55%;
  left: 45%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.28), transparent 70%);
  animation-delay: -16s;
}

@keyframes blobFloat {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%      { transform: translate(28px, -22px) scale(1.06); }
  66%      { transform: translate(-22px, 26px) scale(0.96); }
}

.particles-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

/* ---------- 主容器 ---------- */
.login-container {
  position: relative;
  z-index: 10;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  width: 100%;
  max-width: 1040px;
  /* 不再固定 min-height：让内容决定高度，避免底部署名被裁掉。
     用 align-items: stretch 保证左右两栏等高。 */
  background: var(--color-bg-card);
  border-radius: var(--radius-2xl);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 12px 24px -6px rgba(15, 23, 42, 0.10),
    0 32px 64px -12px rgba(15, 23, 42, 0.16);
  overflow: hidden;
  animation: containerIn 520ms var(--ease-out) both;
}

@keyframes containerIn {
  from { opacity: 0; transform: translateY(16px) scale(0.99); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

/* ---------- 左侧品牌面板（唯一的深色区域）---------- */
.brand-panel {
  position: relative;
  padding: var(--space-8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  background:
    radial-gradient(circle at 15% 12%, rgba(59, 130, 246, 0.30), transparent 45%),
    radial-gradient(circle at 88% 88%, rgba(139, 92, 246, 0.26), transparent 48%),
    linear-gradient(155deg, var(--slate-900) 0%, #16233d 55%, #1a2b4a 100%);
  overflow: hidden;
}

/* 细斜纹，给深色面板一点织物般的质感 */
.brand-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    45deg,
    rgba(255, 255, 255, 0.022) 0 1px,
    transparent 1px 9px
  );
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 380px;
  margin: 0 auto;
  width: 100%;
  text-align: center;
}

.logo-wrapper { margin-bottom: var(--space-6); }

.logo-icon {
  width: 68px;
  height: 68px;
  margin: 0 auto var(--space-4);
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--brand-500) 0%, #7c3aed 100%);
  box-shadow:
    0 8px 20px -4px rgba(37, 99, 235, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.logo-icon svg { width: 34px; height: 34px; }

.brand-title {
  font-size: var(--text-3xl);
  font-weight: 750;
  color: #fff;
  margin: 0 0 var(--space-2);
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.brand-subtitle {
  font-size: var(--text-base);
  color: rgba(203, 213, 225, 0.72);
  margin: 0 0 var(--space-8);
  line-height: var(--leading-relaxed);
}

/* 特性列表 */
.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: var(--radius-lg);
  transition: background var(--transition-normal), border-color var(--transition-normal),
    transform var(--transition-normal);
  opacity: 0;
  animation: featureIn 520ms var(--ease-out) forwards;
}

@keyframes featureIn {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(96, 165, 250, 0.4);
  transform: translateX(4px);
}

.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.icon-tickets { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.icon-monitor { background: linear-gradient(135deg, #22d3ee, #0891b2); }
.icon-user    { background: linear-gradient(135deg, #fbbf24, #f97316); }

.feature-icon svg { width: 18px; height: 18px; color: #fff; }

.feature-text {
  font-size: var(--text-sm);
  font-weight: 500;
  color: #e2e8f0;
  letter-spacing: 0.01em;
}

.brand-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-5);
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: var(--text-xs);
  color: rgba(148, 163, 184, 0.55);
}

/* ---------- 右侧表单面板 ---------- */
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-8);
  background: var(--color-bg-card);
}

.form-container {
  width: 100%;
  max-width: 340px;
}

.form-header { margin-bottom: var(--space-8); }

.form-title {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  letter-spacing: var(--tracking-tight);
}

.form-subtitle {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin: 0;
}

/* 字段间距统一由 el-form-item 的 margin-bottom 控制。
   注意不要再给 .form-field 写 margin-bottom:0 —— 那会把所有间距清零，
   导致"密码"行紧贴"用户名"输入框、甚至视觉重叠。 */
.login-form :deep(.el-form-item) {
  margin-bottom: var(--space-5);
}

.login-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

/* 每个字段内部：标签行与输入框之间留出呼吸感 */
.login-form :deep(.el-form-item__content) {
  display: block;
}

.form-field > :deep(.el-form-item__content) > * {
  width: 100%;
}

/* 记住我：单独压缩，避免与上方输入框距离过大 */
.checkbox-field :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

.submit-field { margin-top: var(--space-2) !important; }

/* 用户名与密码标签行 */
.field-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-2);
}

/* width:100% 必不可少 —— el-form-item__content 是 display:flex;flex-wrap:wrap，
   本元素作为 flex 子项默认按内容收缩（实测只有 86px），
   导致 "密码" 与 "忘记密码?" 挤在一起、space-between 无空间可用。 */
.field-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  width: 100%;
  margin-bottom: var(--space-2);
}

.field-header .field-label { margin: 0; }

.forgot-link {
  font-size: var(--text-xs);
  color: var(--brand-600);
  font-weight: 500;
  transition: color var(--transition-fast);
}

.forgot-link:hover {
  color: var(--brand-700);
  text-decoration: underline;
}

/* 输入框：图标与文字之间的呼吸感 */
.login-form :deep(.el-input__wrapper) {
  padding: 0 var(--space-4) !important;
  background: var(--slate-50) !important;
  box-shadow: 0 0 0 1px var(--color-border) inset !important;
}

.login-form :deep(.el-input__wrapper:hover) {
  background: var(--color-bg-card) !important;
  box-shadow: 0 0 0 1px var(--color-border-strong) inset !important;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: var(--color-bg-card) !important;
  box-shadow: 0 0 0 1px var(--brand-500) inset,
              0 0 0 4px rgba(37, 99, 235, 0.10) !important;
}

.login-form :deep(.el-input__inner) {
  height: 44px !important;
  font-size: var(--text-md) !important;
}

.login-form :deep(.el-input__prefix) {
  color: var(--slate-400) !important;
  font-size: 16px !important;
}

.login-form :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--brand-500) !important;
}

.remember-checkbox :deep(.el-checkbox__label) {
  font-size: var(--text-sm) !important;
  color: var(--color-text-secondary) !important;
}

.submit-btn {
  width: 100%;
  height: 46px !important;
  font-size: var(--text-md) !important;
  font-weight: 600 !important;
  letter-spacing: 0.06em;
  border-radius: var(--radius-lg) !important;
  background: linear-gradient(135deg, var(--brand-600), var(--brand-700)) !important;
  border: none !important;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.20),
              0 8px 20px -6px rgba(37, 99, 235, 0.45) !important;
}

.submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600)) !important;
  box-shadow: 0 2px 4px rgba(37, 99, 235, 0.24),
              0 12px 26px -6px rgba(37, 99, 235, 0.55) !important;
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: var(--shadow-xs) !important;
}

/* 演示账号 */
.form-footer { margin-top: var(--space-5); }

.demo-accounts {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border-light);
}

.demo-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.demo-accounts :deep(.el-button.is-text) {
  height: 26px !important;
  padding: 0 10px !important;
  font-size: var(--text-xs) !important;
  font-weight: 500;
  color: var(--brand-600) !important;
  background: var(--brand-50) !important;
  border-radius: var(--radius-full) !important;
}

.demo-accounts :deep(.el-button.is-text:hover) {
  background: var(--brand-100) !important;
  color: var(--brand-700) !important;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .login-container {
    grid-template-columns: 1fr;
    max-width: 460px;
    min-height: auto;
  }

  /* 小屏隐藏品牌面板，只保留表单，避免内容挤压 */
  .brand-panel { display: none; }

  .form-panel { padding: var(--space-8) var(--space-6); }
}

@media (max-width: 480px) {
  .login-page { padding: var(--space-4); }
  .form-panel { padding: var(--space-6) var(--space-4); }
  .form-title { font-size: var(--text-xl); }
}
</style>