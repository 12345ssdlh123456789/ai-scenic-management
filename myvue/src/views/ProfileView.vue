<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">管理您的账户信息与设置</p>
      </div>
    </div>

    <!-- 头部卡片 -->
    <el-card class="profile-header-card" shadow="never">
      <div class="profile-header-content">
        <div class="avatar-section">
          <div class="avatar avatar-xl" :style="avatarGradient">
            {{ currentUser.username?.charAt(0)?.toUpperCase() }}
          </div>
          <div class="user-info">
            <h2 class="user-name">{{ currentUser.username }}</h2>
            <div class="user-meta">
              <el-tag :type="roleTagType" size="large" effect="dark" class="role-tag">{{ roleText }}</el-tag>
              <span class="user-id">ID: {{ currentUser.id }}</span>
            </div>
          </div>
        </div>

        <div class="balance-section">
          <div class="balance-card">
            <div class="balance-label">账户余额</div>
            <div class="balance-amount">¥{{ balanceDisplay }}</div>
            <el-button type="primary" round @click="showRechargeDialog" class="recharge-btn">
              <Wallet /> 立即充值
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <div class="grid grid-2">
      <!-- 个人信息卡片 -->
      <el-card shadow="never" class="info-card">
        <div class="card-header">
          <h3 class="card-title">
            <UserFilled class="card-icon" /> 个人信息
          </h3>
        </div>
        <div class="card-content">
          <el-descriptions :column="1" border class="descriptions">
            <el-descriptions-item label="用户名">{{ currentUser.username }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="roleTagType" effect="dark">{{ roleText }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ currentUser.email || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ currentUser.phone || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="账户状态">
              <el-tag :type="currentUser.status === 1 ? 'success' : 'danger'" effect="dark">
                {{ currentUser.status === 1 ? '正常' : '已禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="isVip" label="VIP 到期时间">
              {{ formatDate(currentUser.vipExpireTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>

      <!-- 账户操作卡片 -->
      <el-card shadow="never" class="actions-card">
        <div class="card-header">
          <h3 class="card-title">
            <Setting class="card-icon" /> 账户操作
          </h3>
        </div>
        <div class="card-content">
          <div class="action-grid">
            <el-button @click="showPasswordDialog" class="action-btn action-btn--primary">
              <Lock class="action-icon" /> 修改密码
            </el-button>
            <el-button @click="viewRechargeHistory" class="action-btn action-btn--success">
              <CreditCard class="action-icon" /> 充值记录
            </el-button>
            <el-button @click="viewOrderHistory" class="action-btn action-btn--info">
              <Tickets class="action-icon" /> 我的订单
            </el-button>
            <el-button v-if="isUser" @click="viewVipApply" class="action-btn action-btn--warning">
              <Star class="action-icon" /> 申请 VIP
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- VIP 权益卡片 -->
    <el-card v-if="isVip" shadow="never" class="vip-benefits-card" style="margin-top: var(--space-6);">
      <div class="vip-header">
        <div class="vip-badge-wrapper">
          <div class="vip-crown">★</div>
          <div>
            <h3 class="vip-title">VIP 会员尊享权益</h3>
            <p class="vip-expire">有效期至 {{ formatDate(currentUser.vipExpireTime) }}</p>
          </div>
        </div>
        <el-tag type="success" size="large" effect="dark" class="vip-status">VIP 生效中</el-tag>
      </div>
      <div class="vip-benefits">
        <div class="benefit-item" v-for="(benefit, index) in vipBenefits" :key="index">
          <div class="benefit-icon" :class="benefit.iconClass">
            <component :is="benefit.icon" />
          </div>
          <div class="benefit-content">
            <p class="benefit-title">{{ benefit.title }}</p>
            <p class="benefit-desc">{{ benefit.desc }}</p>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 非 VIP 引导卡片 -->
    <el-card v-else shadow="never" class="upgrade-card" style="margin-top: var(--space-6);">
      <div class="upgrade-content">
        <div class="upgrade-icon">
          <Star />
        </div>
        <h3 class="upgrade-title">升级 VIP 会员</h3>
        <p class="upgrade-desc">享受门票 8 折优惠、专属客服、优先入园等特权</p>
        <el-button type="primary" size="large" @click="viewVipApply" class="upgrade-btn">
          <Star /> 立即申请 VIP
        </el-button>
      </div>
    </el-card>
  </div>

  <!-- 修改密码对话框 -->
  <el-dialog v-model="passwordDialogVisible" title="修改密码" width="500px" :close-on-click-modal="false" destroy-on-close>
    <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-position="top" class="dialog-form">
      <el-form-item prop="oldPassword" label="当前密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" size="large" autocomplete="current-password" />
      </el-form-item>
      <el-form-item prop="newPassword" label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" size="large" autocomplete="new-password" />
      </el-form-item>
      <el-form-item prop="confirmPassword" label="确认新密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" size="large" autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPasswordChange" :loading="passwordLoading">确认修改</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 充值对话框 -->
  <el-dialog v-model="rechargeDialogVisible" title="账户充值" width="520px" :close-on-click-modal="false" destroy-on-close>
    <el-form :model="rechargeForm" :rules="rechargeRules" ref="rechargeFormRef" label-position="top" class="dialog-form">
      <el-form-item prop="amount" label="充值金额（元）">
        <el-input-number
          v-model="rechargeForm.amount"
          :min="1"
          :max="10000"
          :precision="2"
          :step="10"
          size="large"
          placeholder="请输入充值金额"
          controls-position="right"
        />
        <template #append>
          <div class="quick-amounts">
            <span v-for="amt in quickAmounts" :key="amt" class="quick-amt" @click="rechargeForm.amount = amt">{{ amt }}</span>
          </div>
        </template>
      </el-form-item>
      <el-alert title="充值须知" type="info" :description="description" show-icon :closable="false" class="recharge-alert" />
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge" :loading="rechargeLoading">提交申请</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser, changePassword } from '@/api/user'
import { applyRecharge } from '@/api/recharge'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { Wallet, Lock, CreditCard, Tickets, Star, Setting, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const passwordFormRef = ref(null)
const rechargeFormRef = ref(null)

const currentUser = computed(() => userStore.loginUser || {})
const isVip = computed(() => currentUser.value?.role === 'VIP')
const isUser = computed(() => currentUser.value?.role === 'USER')

const balance = computed(() => currentUser.value?.balance ? parseFloat(currentUser.value.balance) : 0)
const balanceDisplay = computed(() => balance.value.toFixed(2))

const roleText = computed(() => {
  const map = { USER: '普通用户', VIP: 'VIP 会员', ADMIN: '管理员', SUPER_ADMIN: '超级管理员' }
  return map[currentUser.value?.role] || '未知'
})

const roleTagType = computed(() => {
  const map = { USER: 'info', VIP: 'success', ADMIN: 'warning', SUPER_ADMIN: 'danger' }
  return map[currentUser.value?.role] || 'info'
})

const avatarGradient = computed(() => {
  const gradients = {
    USER: 'linear-gradient(135deg, #64748b 0%, #475569 100%)',
    VIP: 'linear-gradient(135deg, #f59e0b 0%, #f97316 100%)',
    ADMIN: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
    SUPER_ADMIN: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)'
  }
  return { background: gradients[currentUser.value?.role] || gradients.USER }
})

// VIP 权益数据
const vipBenefits = [
  { icon: Tickets, iconClass: 'benefit-tickets', title: '门票 8 折优惠', desc: '全场景区项目门票享受 8 折，不限次数' },
  { icon: Star, iconClass: 'benefit-star', title: '专属客服通道', desc: '7×24 小时专属人工客服响应' },
  { icon: UserFilled, iconClass: 'benefit-user', title: '优先入园', desc: '节假日高峰期享受 VIP 专用通道' },
  { icon: Setting, iconClass: 'benefit-setting', title: '专属活动邀请', desc: '定期受邀参加景区新品体验活动' }
]

// 表单状态
const passwordDialogVisible = ref(false)
const passwordLoading = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_, value) => value === passwordForm.value.newPassword || '两次输入不一致', trigger: 'blur' }
  ]
}

const rechargeDialogVisible = ref(false)
const rechargeLoading = ref(false)
const rechargeForm = ref({ amount: 100 })
const rechargeRules = {
  amount: [
    { required: true, message: '请输入充值金额', trigger: 'blur' },
    { type: 'number', min: 1, max: 10000, message: '金额范围 1-10000 元', trigger: 'blur' }
  ]
}
const quickAmounts = [50, 100, 200, 500, 1000]

const loadUserInfo = async () => {
  try {
    const res = await getCurrentUser()
    userStore.setLoginData(res.data, userStore.token, localStorage.getItem('refreshToken'))
  } catch (e) {
    ElMessage.error('加载用户信息失败')
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const showPasswordDialog = () => {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordDialogVisible.value = true
}

const submitPasswordChange = async () => {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate()
  if (!valid) return

  passwordLoading.value = true
  try {
    const res = await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false
      userStore.clearLoginData()
      router.push('/login')
    } else {
      ElMessage.error(res.message || '密码修改失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    passwordLoading.value = false
  }
}

const showRechargeDialog = () => {
  rechargeForm.value.amount = 100
  rechargeDialogVisible.value = true
}

const submitRecharge = async () => {
  if (!rechargeFormRef.value) return
  const valid = await rechargeFormRef.value.validate()
  if (!valid) return

  rechargeLoading.value = true
  try {
    const res = await applyRecharge({ amount: rechargeForm.value.amount })
    if (res.code === 200) {
      ElMessage.success('充值申请已提交，请等待管理员审核')
      rechargeDialogVisible.value = false
      await loadUserInfo()
    } else {
      ElMessage.error(res.message || '充值申请失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    rechargeLoading.value = false
  }
}

const viewRechargeHistory = () => router.push('/rechargeHistory')
const viewOrderHistory = () => router.push('/myOrders')
const viewVipApply = () => router.push('/vipApply')

onMounted(loadUserInfo)
</script>

<style scoped>
/* ===== 个人中心页面样式 ===== */

/* 网格布局 */
.grid {
  display: grid;
  gap: var(--space-6);
}
.grid-2 { grid-template-columns: repeat(2, 1fr); }
.grid-3 { grid-template-columns: repeat(3, 1fr); }
.grid-4 { grid-template-columns: repeat(4, 1fr); }

@media (max-width: 1024px) {
  .grid-2 { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .grid-3, .grid-4 { grid-template-columns: 1fr; }
}

/* 头部卡片 */
.profile-header-card {
  border: none;
  background: linear-gradient(135deg, var(--color-bg-card) 0%, var(--color-bg) 100%);
  overflow: hidden;
}

.profile-header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-6);
  gap: var(--space-6);
  flex-wrap: wrap;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.avatar-xl {
  width: 80px;
  height: 80px;
  font-size: 32px;
  font-weight: 700;
  box-shadow: var(--shadow-lg);
  border: 3px solid var(--color-bg-card);
}

.user-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.user-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.user-id {
  font-size: 12px;
  color: var(--color-text-tertiary);
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.balance-section {
  flex-shrink: 0;
}

.balance-card {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  border-radius: var(--radius-xl);
  padding: var(--space-6) var(--space-8);
  color: white;
  text-align: right;
  box-shadow: var(--shadow-lg);
  min-width: 200px;
}

.balance-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: var(--space-2);
}

.balance-amount {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin-bottom: var(--space-4);
  font-variant-numeric: tabular-nums;
}

.recharge-btn {
  height: 40px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  background: rgba(255, 255, 255, 0.2) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  backdrop-filter: blur(10px);
}

.recharge-btn:hover {
  background: rgba(255, 255, 255, 0.3) !important;
  border-color: rgba(255, 255, 255, 0.5) !important;
  transform: translateY(-2px);
}

/* 信息卡片 */
.info-card,
.actions-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  transition: all var(--transition-normal);
}

.info-card:hover,
.actions-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-divider);
}

.card-header {
  padding: var(--space-4) var(--space-5) !important;
  border-bottom: 1px solid var(--color-divider) !important;
  margin: calc(-1 * var(--space-5)) calc(-1 * var(--space-5)) var(--space-5) !important;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0 !important;
  background: var(--color-bg);
}

.card-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

/* ⚠️ 图标是 SVG，font-size 对它无效。
   旧规则只写 font-size: 18px，SVG 没有尺寸约束便在 flex 容器里撑满宽度，
   把"个人信息"挤成每行一个字（竖排）。必须显式给 width/height。 */
.card-icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
  color: var(--color-primary);
  flex-shrink: 0;
}

/* 描述列表 */
.descriptions :deep(.el-descriptions__table) {
  width: 100%;
}

.descriptions :deep(.el-descriptions__cell) {
  padding: var(--space-3) var(--space-4) !important;
  font-size: 14px !important;
}

.descriptions :deep(.el-descriptions__label) {
  font-weight: 500 !important;
  color: var(--color-text-secondary) !important;
  width: 120px !important;
  padding-right: var(--space-4) !important;
}

.descriptions :deep(.el-descriptions__content) {
  color: var(--color-text-primary) !important;
}

/* 操作按钮网格 */
.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.el-button.action-btn {
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: center !important;
  /* ⚠️ 选择器必须写成 .el-button.action-btn（特异性 0,2,0）：
     只写 .action-btn 时特异性不足以盖掉 EP 的 `.el-button` 内边距，
     纵向 padding 会被清成 0，图标与文字挤在一起甚至重叠。
     同时显式给 min-height，保证堆叠后的内容装得下。 */
  padding: var(--space-4) !important;
  min-height: 84px !important;
  height: auto !important;
  border-radius: var(--radius-lg) !important;
  font-weight: 500 !important;
  font-size: 14px !important;
  line-height: 1.4 !important;
  border: 1px solid var(--color-border) !important;
  transition: all var(--transition-fast) !important;
  background: var(--color-bg-card) !important;
  /* 必须显式声明文字颜色：这些按钮自定义了白底，
     若沿用 Element Plus 的类型色（primary 为白色文字），
     会出现"白字白底"——按钮看起来完全是空的。
     颜色一律由下方的 .action-btn--* 修饰类提供。 */
  color: var(--color-text-primary) !important;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md) !important;
}

.action-btn:active {
  transform: translateY(0);
}

/* ⚠️ Element Plus 会把按钮内容包进一个 <span style="display:flex">，
   所以只给 .el-button 设 flex-direction: column 不够——那个内层 span
   仍然横排（图标在文字左侧）。必须让内层 span 也纵向排列，
   才能得到"图标在上、文字在下"的方块按钮。

   该 span 由 Element Plus 生成、不带本组件的 scoped 属性，
   因此必须用 :deep() 才能命中。 */
.action-btn :deep(> span) {
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  gap: var(--space-2) !important;
  width: 100%;
}

/* ⚠️ 同为 SVG：font-size 无效。若不限制尺寸，图标会撑满整个网格单元，
   把按钮文字挤出可视区域（表现为"空白按钮"）。务必显式给尺寸。 */
.action-icon {
  width: 22px;
  height: 22px;
  font-size: 22px;
  flex-shrink: 0;
}

/* 按钮类型颜色
   ⚠️ 选择器必须与基础规则 `.el-button.action-btn` 保持同等特异性（0,2,0）。
   基础规则用 `color: ... !important` 兜底（防止 EP 的类型色造成白字白底），
   若这里只写 `.action-btn--primary`（0,1,0），两者都带 !important 时
   基础规则会因特异性更高而胜出，**修饰类的配色会静默失效**——
   按钮仍可见但全部变成默认深灰，失去颜色区分。 */
.el-button.action-btn--primary { color: var(--color-primary) !important; border-color: var(--color-primary) !important; }
.el-button.action-btn--primary:hover { background: var(--color-primary-bg) !important; color: var(--color-primary) !important; }

.el-button.action-btn--success { color: var(--color-success) !important; border-color: var(--color-success) !important; }
.el-button.action-btn--success:hover { background: var(--color-success-bg) !important; color: var(--color-success) !important; }

.el-button.action-btn--info { color: var(--color-info) !important; border-color: var(--color-info) !important; }
.el-button.action-btn--info:hover { background: var(--color-info-bg) !important; color: var(--color-info) !important; }

.el-button.action-btn--warning { color: var(--color-warning) !important; border-color: var(--color-warning) !important; }
.el-button.action-btn--warning:hover { background: var(--color-warning-bg) !important; color: var(--color-warning) !important; }

/* VIP 权益卡片 */
.vip-benefits-card {
  border: 1px solid rgba(245, 158, 11, 0.3) !important;
  background: linear-gradient(135deg, #fffbeb 0%, #fefce8 100%);
  overflow: hidden;
}

.vip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-6);
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
  color: white;
  margin: calc(-1 * var(--space-5)) calc(-1 * var(--space-5)) var(--space-5) !important;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0 !important;
}

.vip-badge-wrapper {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.vip-crown {
  font-size: 36px;
  animation: crownFloat 3s ease-in-out infinite;
}

@keyframes crownFloat {
  0%, 100% { transform: translateY(0) rotate(-5deg); }
  50% { transform: translateY(-6px) rotate(5deg); }
}

.vip-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 var(--space-1);
}

.vip-expire {
  font-size: 13px;
  opacity: 0.9;
  margin: 0;
}

.vip-status {
  font-weight: 600;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.vip-benefits {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
  padding: var(--space-2);
}

@media (max-width: 768px) {
  .vip-benefits { grid-template-columns: 1fr; }
}

.benefit-item {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  background: rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(245, 158, 11, 0.15);
  backdrop-filter: blur(10px);
  transition: all var(--transition-fast);
}

.benefit-item:hover {
  transform: translateX(4px);
  box-shadow: var(--shadow-md);
  border-color: rgba(245, 158, 11, 0.3);
}

.benefit-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.benefit-tickets { background: linear-gradient(135deg, #f59e0b, #f97316); }
.benefit-star { background: linear-gradient(135deg, #ef4444, #f87171); }
.benefit-user { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.benefit-setting { background: linear-gradient(135deg, #7c3aed, #a855f7); }

.benefit-icon svg {
  width: 20px;
  height: 20px;
  color: white;
}

.benefit-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
}

.benefit-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

/* 升级引导卡片 */
.upgrade-card {
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  text-align: center;
  padding: var(--space-10) var(--space-6);
}

.upgrade-content {
  max-width: 400px;
  margin: 0 auto;
}

.upgrade-icon {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--space-5);
  box-shadow: var(--shadow-lg);
}

.upgrade-icon svg {
  width: 32px;
  height: 32px;
  color: white;
}

.upgrade-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-3);
}

.upgrade-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0 0 var(--space-6);
  line-height: 1.6;
}

.upgrade-btn {
  height: 48px !important;
  padding: 0 32px !important;
  font-weight: 600 !important;
  font-size: 15px !important;
}

/* 对话框表单 */
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--space-5) !important;
}

.dialog-form :deep(.el-input__inner) {
  height: 44px !important;
  font-size: 14px !important;
  border-radius: var(--radius-md) !important;
}

.dialog-form :deep(.el-input-number__input) {
  height: 42px !important;
  font-size: 15px !important;
}

.dialog-form :deep(.el-input-number__decrease),
.dialog-form :deep(.el-input-number__increase) {
  width: 36px !important;
  border-radius: var(--radius-md) !important;
  border: 1px solid var(--color-border) !important;
  background: var(--color-bg) !important;
  color: var(--color-text-secondary) !important;
}

.dialog-form :deep(.el-input-number__decrease:hover),
.dialog-form :deep(.el-input-number__increase:hover) {
  background: var(--color-primary-bg) !important;
  color: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
}

.quick-amounts {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-divider);
}

.quick-amt {
  font-size: 12px;
  color: var(--color-primary);
  background: var(--color-primary-bg);
  padding: 4px 10px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.quick-amt:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.05);
}

.recharge-alert {
  margin-top: var(--space-2) !important;
  padding: var(--space-3) var(--space-4) !important;
  border-radius: var(--radius-md) !important;
  font-size: 13px !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  margin: 0 calc(-1 * var(--space-6)) calc(-1 * var(--space-6)) !important;
  padding-left: var(--space-6) !important;
  padding-right: var(--space-6) !important;
  padding-bottom: var(--space-6) !important;
}

/* 响应式 */
@media (max-width: 768px) {
  .profile-header-content {
    flex-direction: column;
    text-align: center;
  }

  .avatar-section {
    flex-direction: column;
    text-align: center;
  }

  .user-meta {
    justify-content: center;
  }

  .balance-card {
    text-align: center;
    min-width: auto;
    width: 100%;
  }

  .action-grid {
    grid-template-columns: 1fr;
  }
}
</style>