<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">VIP 会员申请</h1>
        <p class="page-subtitle">申请 VIP 会员，享受专属权益与优惠</p>
      </div>
    </div>

    <el-card shadow="never" class="vip-benefits-showcase">
      <div class="vip-showcase-header">
        <div class="vip-crown-wrapper">
          <div class="vip-crown">★</div>
        </div>
        <div class="vip-showcase-text">
          <h3 class="vip-showcase-title">成为 VIP 会员</h3>
          <p class="vip-showcase-desc">解锁专属特权，畅享景区优惠体验</p>
        </div>
      </div>
      <div class="benefits-grid">
        <div class="benefit-showcase-item" v-for="(benefit, index) in vipBenefits" :key="index" :style="{ transitionDelay: `${index * 80}ms` }">
          <div class="benefit-showcase-icon" :class="benefit.iconClass">
            <el-icon><component :is="benefit.icon" /></el-icon>
          </div>
          <div class="benefit-showcase-content">
            <h4 class="benefit-showcase-title">{{ benefit.title }}</h4>
            <p class="benefit-showcase-desc">{{ benefit.desc }}</p>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="application-card">
      <div v-if="myApp" class="status-display">
        <div class="status-header">
          <div class="status-badge" :class="getStatusClass(myApp.status)">
            <div class="status-icon">
              <el-icon v-if="myApp.status === 'PENDING'"><Clock /></el-icon>
              <el-icon v-else-if="myApp.status === 'APPROVED'"><Check /></el-icon>
              <el-icon v-else><Close /></el-icon>
            </div>
            <div class="status-text-group">
              <span class="status-label">申请状态</span>
              <span class="status-value">{{ getStatusName(myApp.status) }}</span>
            </div>
          </div>

          <div class="status-timeline">
            <div class="timeline-item" :class="{ active: true, done: ['APPROVED', 'REJECTED'].includes(myApp.status) }">
              <div class="timeline-node"></div>
              <div class="timeline-content">
                <span class="timeline-title">提交申请</span>
                <span class="timeline-time">{{ formatDateTime(myApp.createTime) }}</span>
              </div>
            </div>
            <div class="timeline-item" :class="{ active: ['APPROVED', 'REJECTED'].includes(myApp.status), done: ['APPROVED', 'REJECTED'].includes(myApp.status) }">
              <div class="timeline-node"></div>
              <div class="timeline-content">
                <span class="timeline-title">管理员审核</span>
                <span class="timeline-time">{{ myApp.updateTime ? formatDateTime(myApp.updateTime) : '进行中...' }}</span>
              </div>
            </div>
            <div class="timeline-item" v-if="myApp.status === 'APPROVED'" :class="{ active: true, done: true }">
              <div class="timeline-node"></div>
              <div class="timeline-content">
                <span class="timeline-title">VIP 已激活</span>
                <span class="timeline-time">即时生效，享受权益</span>
              </div>
            </div>
          </div>

          <div class="status-details">
            <div class="detail-group" v-if="myApp.applyReason">
              <span class="detail-label">申请理由</span>
              <p class="detail-value">{{ myApp.applyReason }}</p>
            </div>
            <div class="detail-group" v-if="myApp.adminRemark">
              <span class="detail-label">管理员回复</span>
              <p class="detail-value admin-reply">{{ myApp.adminRemark }}</p>
            </div>
          </div>

          <div class="status-actions">
            <el-button v-if="myApp.status === 'REJECTED'" type="primary" @click="showReapplyDialog" class="reapply-btn">
              <el-icon><Refresh /></el-icon> 重新申请
            </el-button>
            <el-button v-if="myApp.status === 'APPROVED'" variant="text" @click="router.push('/profile')" class="view-profile-btn">
              <el-icon><UserFilled /></el-icon> 查看个人中心
            </el-button>
          </div>
        </div>
      </div>

      <div v-else class="apply-form-section">
        <div class="form-header">
          <div class="form-icon">
            <el-icon><Star /></el-icon>
          </div>
          <h3 class="form-title">提交 VIP 申请</h3>
          <p class="form-desc">填写申请理由，管理员将在 1-2 个工作日内审核</p>
        </div>

        <el-form :model="applyForm" :rules="applyRules" ref="applyFormRef" label-position="top" class="dialog-form">
          <el-form-item prop="applyReason" label="申请理由">
            <el-input
              v-model="applyForm.applyReason"
              type="textarea"
              :rows="5"
              placeholder="请详细说明您申请 VIP 的理由，例如：经常游玩、需要优惠、想要专属服务等..."
              size="large"
              show-word-limit
              maxlength="500"
              autocomplete="off"
            />
          </el-form-item>

          <!-- ⚠️ 不要把数组直接绑给 el-alert 的 :description。
              它最终会走 String(值)，页面上会显示成一串原始 JSON：
              ["VIP 会员享受...", "审核通过后...", ...]
              这里改为默认插槽 + 列表渲染，语义和观感都正确。 -->
          <el-alert
            title="温馨提示"
            type="info"
            show-icon
            :closable="false"
            class="tips-alert"
          >
            <ul class="tips-list">
              <li>VIP 会员享受全场项目门票 8 折优惠</li>
              <li>审核通过后立即生效，有效期 30 天</li>
              <li>每个账号仅需申请一次，通过后自动激活</li>
              <li>如有疑问请联系在线客服</li>
            </ul>
          </el-alert>

          <el-form-item class="submit-field">
            <el-button
              type="primary"
              size="large"
              @click="handleApply"
              :loading="loading"
              class="submit-btn"
            >
              <el-icon><Star /></el-icon> 提交申请
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <el-dialog
      v-model="reapplyDialogVisible"
      title="重新提交 VIP 申请"
      width="580px"
      :close-on-click-modal="false"
      destroy-on-close
      class="reapply-dialog"
    >
      <el-form :model="reapplyForm" :rules="applyRules" ref="reapplyFormRef" label-position="top" class="dialog-form">
        <el-form-item prop="applyReason" label="申请理由">
          <el-input
            v-model="reapplyForm.applyReason"
            type="textarea"
            :rows="5"
            placeholder="请重新填写申请理由..."
            size="large"
            show-word-limit
            maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="reapplyDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleReapply" :loading="reapplyLoading">
            <el-icon><Star /></el-icon> 确认提交
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { applyVip, getMyVipApplication } from '@/api/vip'
import { ElMessage } from 'element-plus'
import {
  Star, UserFilled, Ticket, Headset, Lock, Refresh,
  Clock, Close, Check, ArrowRight
} from '@element-plus/icons-vue'

const router = useRouter()

const myApp = ref(null)
const loading = ref(false)

const applyForm = ref({ applyReason: '' })
const reapplyForm = ref({ applyReason: '' })
const reapplyDialogVisible = ref(false)
const reapplyLoading = ref(false)
const applyFormRef = ref(null)
const reapplyFormRef = ref(null)

const applyRules = {
  applyReason: [
    { required: true, message: '请填写申请理由', trigger: 'blur' },
    { min: 10, message: '申请理由至少 10 字符', trigger: 'blur' },
    { max: 500, message: '最多 500 字符', trigger: 'blur' }
  ]
}

const vipBenefits = [
  { icon: Ticket, iconClass: 'benefit-ticket', title: '门票 8 折优惠', desc: '全场景区项目门票享受 8 折，不限次数使用' },
  { icon: Headset, iconClass: 'benefit-headset', title: '专属客服通道', desc: '7×24 小时专属人工客服优先响应' },
  { icon: Lock, iconClass: 'benefit-lock', title: '优先入园通道', desc: '节假日高峰期享受 VIP 专用快速通道' },
  { icon: ArrowRight, iconClass: 'benefit-arrow', title: '专属活动邀请', desc: '定期受邀参加景区新品体验、会员专属活动' }
]

const getStatusName = (status) => {
  const map = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '已拒绝' }
  return map[status] || status
}

const getStatusClass = (status) => {
  const map = { PENDING: 'status-pending', APPROVED: 'status-approved', REJECTED: 'status-rejected' }
  return map[status] || ''
}

const formatDateTime = (dateTime) => {
  if (!dateTime) return '—'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false
  }).replace(/\//g, '-')
}

const loadApplication = async () => {
  loading.value = true
  try {
    const res = await getMyVipApplication()
    if (res.code === 200 && res.data) {
      myApp.value = res.data
    }
  } catch (e) {
    // 无申请记录
  } finally {
    loading.value = false
  }
}

const handleApply = async () => {
  if (!applyFormRef.value) return
  const valid = await applyFormRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    const res = await applyVip(applyForm.value)
    if (res.code === 200) {
      ElMessage.success('申请已提交，等待管理员审核')
      applyForm.value.applyReason = ''
      await loadApplication()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('提交失败')
  } finally {
    loading.value = false
  }
}

const showReapplyDialog = () => {
  reapplyForm.value.applyReason = ''
  reapplyDialogVisible.value = true
}

const handleReapply = async () => {
  if (!reapplyFormRef.value) return
  const valid = await reapplyFormRef.value.validate()
  if (!valid) return

  reapplyLoading.value = true
  try {
    const res = await applyVip(reapplyForm.value)
    if (res.code === 200) {
      ElMessage.success('重新申请已提交')
      reapplyDialogVisible.value = false
      await loadApplication()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('提交失败')
  } finally {
    reapplyLoading.value = false
  }
}

onMounted(loadApplication)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.vip-benefits-showcase {
  border: 1px solid rgba(245, 158, 11, 0.3) !important;
  background: linear-gradient(135deg, #fffbeb 0%, #fefce8 100%);
  border-radius: var(--radius-xl) !important;
  overflow: hidden;
  margin-bottom: var(--space-6);
}

.vip-showcase-header {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
  color: white;
  margin: calc(-1 * var(--space-5)) calc(-1 * var(--space-5)) var(--space-5) !important;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0 !important;
}

.vip-crown-wrapper {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  animation: crownFloat 3s ease-in-out infinite;
}

@keyframes crownFloat {
  0%, 100% { transform: translateY(0) rotate(-5deg); }
  50% { transform: translateY(-6px) rotate(5deg); }
}

.vip-crown {
  font-size: 28px;
}

.vip-showcase-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 var(--space-1);
}

.vip-showcase-desc {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
}

.benefits-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  padding: var(--space-2) var(--space-6) var(--space-6);
}

@media (max-width: 1024px) {
  .benefits-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .benefits-grid { grid-template-columns: 1fr; }
}

.benefit-showcase-item {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  background: rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(245, 158, 11, 0.15);
  backdrop-filter: blur(10px);
  transition: all var(--transition-fast);
  opacity: 0;
  transform: translateY(20px);
  animation: benefitSlideIn 0.6s ease forwards;
}

@keyframes benefitSlideIn {
  to { opacity: 1; transform: translateY(0); }
}

.benefit-showcase-item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
  border-color: rgba(245, 158, 11, 0.3);
}

.benefit-showcase-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.benefit-ticket { background: linear-gradient(135deg, #f59e0b, #f97316); }
.benefit-headset { background: linear-gradient(135deg, #ef4444, #f87171); }
.benefit-lock { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.benefit-arrow { background: linear-gradient(135deg, #7c3aed, #a855f7); }

.benefit-showcase-icon svg {
  width: 22px;
  height: 22px;
  color: white;
}

.benefit-showcase-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
}

.benefit-showcase-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.application-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
}

.status-display {
  padding: var(--space-6);
}

.status-header {
  margin-bottom: var(--space-6);
}

.status-badge {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border-radius: var(--radius-lg);
  max-width: 400px;
}

.status-badge.status-pending {
  background: var(--color-warning-bg);
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.status-badge.status-approved {
  background: var(--color-success-bg);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.status-badge.status-rejected {
  background: var(--color-danger-bg);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.status-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-icon :deep(svg) {
  width: 24px;
  height: 24px;
  color: white;
}

.status-badge.status-pending .status-icon { background: var(--color-warning); }
.status-badge.status-approved .status-icon { background: var(--color-success); }
.status-badge.status-rejected .status-icon { background: var(--color-danger); }

.status-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.status-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.status-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.status-timeline {
  position: relative;
  padding-left: var(--space-5);
  margin-bottom: var(--space-6);
}

.status-timeline::before {
  content: '';
  position: absolute;
  left: 10px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-divider);
  border-radius: 1px;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: var(--space-4);
  padding-bottom: var(--space-6);
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-node {
  position: relative;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-full);
  background: var(--color-bg-card);
  border: 2px solid var(--color-divider);
  flex-shrink: 0;
  z-index: 1;
  transition: all var(--transition-normal);
}

.timeline-item.active .timeline-node {
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-bg);
}

.timeline-item.done .timeline-node {
  background: var(--color-success);
  border-color: var(--color-success);
}

.timeline-content {
  flex: 1;
}

.timeline-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.timeline-time {
  display: block;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.status-details {
  margin-bottom: var(--space-6);
  padding: var(--space-5);
  background: var(--color-bg);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
}

.detail-group {
  margin-bottom: var(--space-4);
}

.detail-group:last-child {
  margin-bottom: 0;
}

.detail-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: var(--space-2);
}

.detail-value {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
}

.detail-value.admin-reply {
  color: var(--color-primary);
  background: var(--color-primary-bg);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);
}

.status-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.reapply-btn {
  height: 40px !important;
  padding: 0 24px !important;
  font-weight: 600 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: var(--space-2) !important;
}

.view-profile-btn {
  height: 40px !important;
  padding: 0 24px !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: var(--space-2) !important;
}

.apply-form-section {
  padding: var(--space-6);
}

.form-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-divider);
}

.form-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--shadow-md);
}

.form-icon svg {
  width: 28px;
  height: 28px;
  color: white;
}

.form-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
}

.form-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.tips-alert {
  margin-top: var(--space-4) !important;
  padding: var(--space-4) !important;
  border-radius: var(--radius-md) !important;
  font-size: 13px !important;
}

/* 提示条目列表（默认插槽内容） */
.tips-list {
  margin: var(--space-1) 0 0;
  padding-left: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  line-height: var(--leading-normal);
}

.tips-list li {
  list-style: disc;
}

.submit-field {
  margin-top: var(--space-4) !important;
}

.submit-btn {
  width: 100%;
  height: 48px !important;
  font-weight: 600 !important;
  font-size: 15px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: var(--space-2) !important;
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(245, 158, 11, 0.3) !important;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px) !important;
  box-shadow: 0 6px 20px rgba(245, 158, 11, 0.4) !important;
}

.reapply-dialog :deep(.el-dialog__header) {
  padding: var(--space-5) var(--space-6) !important;
  border-bottom: 1px solid var(--color-divider) !important;
}

.reapply-dialog :deep(.el-dialog__title) {
  font-size: 18px !important;
  font-weight: 600 !important;
}

.reapply-dialog :deep(.el-dialog__body) {
  padding: var(--space-6) !important;
}

.reapply-dialog :deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-6) !important;
  border-top: 1px solid var(--color-divider) !important;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--space-5) !important;
}

.dialog-form :deep(.el-form-item__label) {
  font-size: 13px !important;
  font-weight: 500 !important;
  color: var(--color-text-secondary) !important;
  padding: 0 0 var(--space-2) !important;
}

.dialog-form :deep(.el-textarea__inner) {
  font-size: 14px !important;
  border-radius: var(--radius-md) !important;
  padding: var(--space-3) !important;
  min-height: 120px !important;
}

.dialog-form :deep(.el-textarea__inner:focus) {
  border-color: var(--color-primary) !important;
  box-shadow: 0 0 0 3px var(--color-primary-bg) !important;
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

@media (max-width: 768px) {
  .vip-showcase-header {
    flex-direction: column;
    text-align: center;
  }

  .benefits-grid {
    padding: var(--space-2) var(--space-4) var(--space-4);
  }

  .status-badge {
    flex-direction: column;
    text-align: center;
    padding: var(--space-4);
  }

  .status-timeline {
    padding-left: var(--space-4);
  }

  .status-timeline::before {
    left: 9px;
  }

  .timeline-node {
    width: 18px;
    height: 18px;
  }
}
</style>