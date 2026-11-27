<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">充值记录</h1>
        <p class="page-subtitle">查看您的充值申请与审核进度</p>
      </div>
      <el-button variant="text" @click="goToProfile" class="back-btn">
        <ArrowLeft /> 返回个人中心
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card shadow="never" class="stat-card" v-for="(stat, index) in rechargeStats" :key="index">
        <div class="stat-content">
          <div class="stat-icon" :class="stat.iconClass">
            <component :is="stat.icon" />
          </div>
          <div class="stat-info">
            <p class="stat-value">{{ stat.value }}</p>
            <p class="stat-label">{{ stat.label }}</p>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-bar">
      <el-form :model="searchForm" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" size="small" style="width: 150px" clearable>
            <el-option label="全部" value="" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            size="small"
            style="width: 280px"
            :picker-options="pickerOptions"
            placeholder="选择申请日期"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadApplications" :loading="loading" size="small">
            <Search /> 搜索
          </el-button>
          <el-button @click="handleReset" size="small">
            <Refresh /> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 充值记录列表 -->
    <el-card shadow="never" class="recharge-card">
      <!-- 加载骨架屏 -->
      <div v-if="loading" class="recharge-skeleton">
        <div class="skeleton-recharge" v-for="i in 5" :key="i">
          <div class="skeleton-row">
            <div class="skeleton" style="width: 80px; height: 20px;"></div>
            <div class="skeleton" style="width: 100px; height: 28px; border-radius: var(--radius-full);"></div>
            <div class="skeleton" style="width: 200px; height: 20px;"></div>
            <div class="skeleton" style="width: 180px; height: 20px;"></div>
            <div class="skeleton" style="width: 180px; height: 20px;"></div>
          </div>
        </div>
      </div>

      <!-- 记录卡片 -->
      <div v-else-if="applications.length > 0" class="recharge-list">
        <div
          v-for="app in applications"
          :key="app.id"
          class="recharge-item"
          :class="getStatusClass(app.status)"
        >
          <div class="recharge-main">
            <div class="recharge-id">
              <span class="id-label">申请单号</span>
              <span class="id-value" :title="app.id">{{ formatId(app.id) }}</span>
            </div>
            <div class="recharge-amount">
              <span class="amount-label">充值金额</span>
              <span class="amount-value">+¥{{ formatMoney(app.amount) }}</span>
            </div>
            <el-tag :type="getStatusTagType(app.status)" effect="dark" size="large" class="status-tag">
              {{ getStatusName(app.status) }}
            </el-tag>
          </div>

          <div class="recharge-meta">
            <div class="meta-group">
              <span class="meta-label">申请时间</span>
              <span class="meta-value">{{ formatDateTime(app.createTime) }}</span>
            </div>
            <div class="meta-group" v-if="app.updateTime && app.status !== 'PENDING'">
              <span class="meta-label">处理时间</span>
              <span class="meta-value">{{ formatDateTime(app.updateTime) }}</span>
            </div>
            <div class="meta-group" v-if="app.adminRemark">
              <span class="meta-label">审核备注</span>
              <span class="meta-value remark">{{ app.adminRemark }}</span>
            </div>
          </div>

          <div class="recharge-progress" v-if="app.status === 'PENDING'">
            <el-progress :percentage="pendingProgress" :stroke-width="4" :show-text="false" color="#f59e0b" />
            <p class="progress-text">正在审核中，预计 1-2 个工作日完成</p>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state-full">
        <div class="empty-illustration">
          <CreditCard />
        </div>
        <h3 class="empty-title">暂无充值记录</h3>
        <p class="empty-desc">
          <span v-if="searchForm.status || searchForm.dateRange?.length">尝试调整筛选条件</span>
          <span v-else>您还没有提交过充值申请</span>
        </p>
        <div class="empty-actions">
          <el-button v-if="!searchForm.status && !searchForm.dateRange?.length" type="primary" size="large" @click="router.push('/profile')">
            <Wallet /> 去充值
          </el-button>
          <el-button v-else @click="handleReset">
            <Refresh /> 清除筛选
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && applications.length > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyRecharge } from '@/api/recharge'
import { formatMoney } from '@/utils/format'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, ArrowLeft, Wallet, CreditCard, CircleCheck,
  Clock
} from '@element-plus/icons-vue'

const router = useRouter()

const applications = ref([])
const loading = ref(false)

const searchForm = ref({
  status: '',
  dateRange: []
})

const pagination = ref({ current: 1, size: 10, total: 0 })

const pickerOptions = {
  shortcuts: [
    { text: '最近一周', value: () => { const end = new Date(); const start = new Date(); start.setDate(end.getDate() - 7); return [start, end] } },
    { text: '最近一个月', value: () => { const end = new Date(); const start = new Date(); start.setMonth(end.getMonth() - 1); return [start, end] } },
    { text: '最近三个月', value: () => { const end = new Date(); const start = new Date(); start.setMonth(end.getMonth() - 3); return [start, end] } }
  ]
}

// 统计数据
const rechargeStats = computed(() => {
  const total = applications.value.length
  const pending = applications.value.filter(a => a.status === 'PENDING').length
  const approved = applications.value.filter(a => a.status === 'APPROVED').length
  const totalAmount = applications.value
    .filter(a => a.status === 'APPROVED')
    .reduce((sum, a) => sum + parseFloat(a.amount || 0), 0)

  return [
    { label: '全部申请', value: total, icon: CreditCard, iconClass: 'stat-bank' },
    { label: '待审核', value: pending, icon: Clock, iconClass: 'stat-clock' },
    { label: '已到账', value: approved, icon: CircleCheck, iconClass: 'stat-check' },
    { label: '成功金额', value: '¥' + totalAmount.toFixed(2), icon: Wallet, iconClass: 'stat-wallet' }
  ]
})

const getStatusName = (status) => {
  const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
  return map[status] || status
}

const getStatusTagType = (status) => {
  const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

const getStatusClass = (status) => {
  const map = { PENDING: 'status-pending', APPROVED: 'status-approved', REJECTED: 'status-rejected' }
  return map[status] || ''
}

const formatId = (id) => String(id).padStart(8, '0')

const formatDateTime = (dateTime) => {
  if (!dateTime) return '—'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false
  }).replace(/\//g, '-')
}

const pendingProgress = computed(() => {
  // 模拟进度：根据创建时间计算
  return 30 + Math.random() * 40
})

const loadApplications = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.status) params.status = searchForm.value.status
    if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
      params.startTime = searchForm.value.dateRange[0] + ' 00:00:00'
      params.endTime = searchForm.value.dateRange[1] + ' 23:59:59'
    }

    const res = await getMyRecharge(params)
    if (res.code === 200) {
      const data = res.data || {}
      applications.value = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
      pagination.value.total = data.total || applications.value.length
    }
  } catch (e) {
    ElMessage.error('加载失败')
    applications.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.value = { status: '', dateRange: [] }
  pagination.value.current = 1
  loadApplications()
}

const handlePageChange = (page) => {
  pagination.value.current = page
  loadApplications()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  loadApplications()
}

const goToProfile = () => router.push('/profile')

onMounted(loadApplications)
</script>

<style scoped>
/* ===== 充值记录页面样式 ===== */

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.back-btn {
  height: 36px !important;
  padding: 0 16px !important;
  font-size: 13px !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: var(--space-2) !important;
  color: var(--color-text-secondary) !important;
}

.back-btn:hover {
  color: var(--color-primary) !important;
  background: var(--color-primary-bg) !important;
}

/* 统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

@media (max-width: 1024px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .stats-grid { grid-template-columns: 1fr; }
}

.stat-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-card);
  transition: all var(--transition-normal);
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-divider);
  transform: translateY(-2px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.stat-bank { background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%); }
.stat-clock { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.stat-check { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.stat-wallet { background: linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%); }

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* 筛选栏 */
.filter-bar {
  margin-bottom: var(--space-6);
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0 !important;
  margin-right: var(--space-4) !important;
}

.filter-form :deep(.el-form-item__label) {
  font-size: 13px !important;
  font-weight: 500 !important;
  color: var(--color-text-secondary) !important;
  padding: 0 !important;
  line-height: 36px;
}

.filter-form :deep(.el-input__inner),
.filter-form :deep(.el-select .el-input__inner),
.filter-form :deep(.el-date-editor .el-input__inner) {
  height: 36px !important;
  font-size: 13px !important;
}

/* 充值记录卡片 */
.recharge-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.recharge-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4);
}

.recharge-item {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-normal);
}

.recharge-item:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-divider);
}

.recharge-item.status-pending {
  border-left: 3px solid var(--color-warning);
}

.recharge-item.status-approved {
  border-left: 3px solid var(--color-success);
}

.recharge-item.status-rejected {
  opacity: 0.7;
  background: var(--color-bg);
  border-left: 3px solid var(--color-danger);
}

/* 主要信息 */
.recharge-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  flex-wrap: wrap;
  gap: var(--space-3);
}

.recharge-id {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.id-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.id-value {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  background: var(--color-bg-card);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.recharge-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.amount-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.amount-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-success);
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.status-tag {
  font-weight: 500 !important;
  font-size: 12px !important;
  min-width: 80px;
}

/* 元信息 */
.recharge-meta {
  display: flex;
  gap: var(--space-6);
  padding: var(--space-4) var(--space-5);
  flex-wrap: wrap;
}

.meta-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 160px;
}

.meta-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.meta-value {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.meta-value.remark {
  color: var(--color-text-primary);
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 审核进度 */
.recharge-progress {
  padding: var(--space-4) var(--space-5);
  background: var(--color-warning-bg);
  border-top: 1px solid var(--color-warning);
}

.recharge-progress :deep(.el-progress-bar__outer) {
  border-radius: var(--radius-full) !important;
}

.recharge-progress :deep(.el-progress-bar__inner) {
  border-radius: var(--radius-full) !important;
}

.progress-text {
  font-size: 12px;
  color: var(--color-warning);
  margin: var(--space-2) 0 0;
  text-align: center;
}

/* 空状态 */
.empty-state-full {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.empty-illustration {
  width: 96px;
  height: 96px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-6);
  font-size: 40px;
}

.empty-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.empty-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0 0 var(--space-6);
  max-width: 300px.
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6).
}

.pagination :deep(.el-pager li) {
  border-radius: var(--radius-md) !important;
  margin: 0 var(--space-1) !important;
}

.pagination :deep(.el-pager li.active) {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
}

/* 骨架屏 */
.recharge-skeleton {
  padding: var(--space-4);
}

.skeleton-recharge {
  padding: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}

.skeleton-recharge:last-child {
  border-bottom: none.
}

.skeleton-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 响应式 */
@media (max-width: 768px) {
  .recharge-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .recharge-amount {
    align-items: flex-start;
    width: 100%;
  }

  .recharge-meta {
    flex-direction: column;
    gap: var(--space-3);
  }

  .meta-group {
    width: 100%;
  }
}
</style>