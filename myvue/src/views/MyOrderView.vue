<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的订单</h1>
        <p class="page-subtitle">查看和管理您的所有门票订单</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card shadow="never" class="stat-card" v-for="(stat, index) in orderStats" :key="index">
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
        <el-form-item label="项目名称">
          <el-input
            v-model="searchForm.projectName"
            placeholder="搜索项目..."
            clearable
            size="small"
            style="width: 220px"
            @keyup.enter="loadOrders"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" size="small" style="width: 150px" clearable>
            <el-option label="全部" value="" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="退款审核中" value="REFUND_PENDING" />
            <el-option label="已退款" value="REFUNDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            size="small"
            style="width: 280px"
            :picker-options="pickerOptions"
            placeholder="选择日期范围"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading" size="small">
            <Search /> 搜索
          </el-button>
          <el-button @click="handleReset" size="small">
            <Refresh /> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单列表 -->
    <el-card shadow="never" class="orders-card">
      <!-- 加载骨架屏 -->
      <div v-if="loading" class="orders-skeleton">
        <div class="skeleton-order" v-for="i in 5" :key="i">
          <div class="skeleton-row">
            <div class="skeleton" style="width: 120px; height: 20px;"></div>
            <div class="skeleton" style="width: 180px; height: 20px;"></div>
            <div class="skeleton" style="width: 80px; height: 20px;"></div>
            <div class="skeleton" style="width: 100px; height: 20px;"></div>
            <div class="skeleton" style="width: 180px; height: 20px;"></div>
            <div class="skeleton" style="width: 80px; height: 28px; border-radius: var(--radius-full);"></div>
          </div>
          <div class="skeleton-actions">
            <div class="skeleton" style="width: 70px; height: 32px; border-radius: var(--radius-md);"></div>
            <div class="skeleton" style="width: 70px; height: 32px; border-radius: var(--radius-md);"></div>
          </div>
        </div>
      </div>

      <!-- 订单卡片列表 -->
      <div v-else-if="orders.length > 0" class="orders-list">
        <div
          v-for="order in orders"
          :key="order.id"
          class="order-card"
          :class="getOrderCardClass(order.status)"
        >
          <div class="order-header">
            <div class="order-id-group">
              <span class="order-id-label">订单号</span>
              <span class="order-id" :title="order.id">{{ formatOrderId(order.id) }}</span>
              <el-tag :type="getStatusTagType(order.status)" effect="dark" size="small" class="status-tag">
                {{ getStatusName(order.status) }}
              </el-tag>
            </div>
            <div class="order-time">
              <span class="time-label">下单时间</span>
              <span class="time-value">{{ formatDateTime(order.orderTime) }}</span>
            </div>
          </div>

          <div class="order-body">
            <div class="project-info">
              <div class="project-avatar" :style="getProjectAvatarStyle(order.projectName)">
                <component :is="getProjectIconComponent(order.projectName)" />
              </div>
              <div class="project-detail">
                <h4 class="project-name">{{ order.projectName }}</h4>
                <div class="project-meta">
                  <span class="meta-item">
                    <Ticket /> 数量：{{ order.quantity }} 张
                  </span>
                  <span class="meta-item">
                    <Money /> 单价：¥{{ formatMoney(order.unitPrice) }}
                  </span>
                </div>
              </div>
            </div>

            <div class="order-total">
              <span class="total-label">订单总额</span>
              <span class="total-value">¥{{ formatMoney(order.totalPrice) }}</span>
            </div>
          </div>

          <!-- 操作区。此前这里第一个子元素是一条孤立的竖向 el-divider，
               它前面没有任何内容，渲染出来就是按钮左侧一个突兀的"|"。
               分隔作用已由 .order-actions 的 border-top 承担，故移除。 -->
          <div class="order-actions">
            <el-button
              v-if="order.status === 'PAID'"
              size="small"
              type="danger"
              @click="handleCancel(order.id)"
              class="action-btn"
            >
              <Delete /> 取消订单
            </el-button>
            <el-button
              v-if="['PAID', 'COMPLETED'].includes(order.status)"
              size="small"
              type="warning"
              @click="handleRefund(order.id)"
              class="action-btn"
            >
              <Money /> 申请退款
            </el-button>
            <el-button
              v-if="order.status === 'PAID'"
              size="small"
              type="primary"
              @click="viewDetail(order)"
              class="action-btn"
            >
              <Document /> 查看详情
            </el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state-full">
        <div class="empty-illustration">
          <Document />
        </div>
        <h3 class="empty-title">暂无订单</h3>
        <p class="empty-desc">
          <span v-if="searchForm.projectName || searchForm.status || searchForm.dateRange">
            尝试调整筛选条件
          </span>
          <span v-else>去景点项目页面挑选心仪的门票吧</span>
        </p>
        <div class="empty-actions">
          <el-button v-if="!searchForm.projectName && !searchForm.status && !searchForm.dateRange" type="primary" size="large" @click="router.push('/projects')">
            <Tickets /> 去选购门票
          </el-button>
          <el-button v-else @click="handleReset">
            <Refresh /> 清除筛选
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && orders.length > 0" class="pagination-wrapper">
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
import { getMyOrders } from '@/api/order'
import request from '@/utils/request'
import { formatMoney } from '@/utils/format'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Ticket, Money, Delete,
  Document, Tickets, Monitor, Location
} from '@element-plus/icons-vue'

const router = useRouter()

const orders = ref([])
const loading = ref(false)

const searchForm = ref({
  projectName: '',
  status: '',
  dateRange: []
})

const pagination = ref({ current: 1, size: 10, total: 0 })

// 日期选择器快捷选项
const pickerOptions = {
  shortcuts: [
    { text: '最近一周', value: () => { const end = new Date(); const start = new Date(); start.setDate(end.getDate() - 7); return [start, end] } },
    { text: '最近一个月', value: () => { const end = new Date(); const start = new Date(); start.setMonth(end.getMonth() - 1); return [start, end] } },
    { text: '最近三个月', value: () => { const end = new Date(); const start = new Date(); start.setMonth(end.getMonth() - 3); return [start, end] } }
  ]
}

// 计算订单统计
const orderStats = computed(() => {
  const stats = {
    total: orders.value.length,
    paid: orders.value.filter(o => o.status === 'PAID').length,
    completed: orders.value.filter(o => o.status === 'COMPLETED').length,
    cancelled: orders.value.filter(o => o.status === 'CANCELLED').length,
    refunded: orders.value.filter(o => o.status === 'REFUNDED').length,
    totalAmount: orders.value.reduce((sum, o) => sum + parseFloat(o.totalPrice || 0), 0)
  }
  return [
    { label: '全部订单', value: stats.total, icon: Tickets, iconClass: 'stat-tickets' },
    { label: '进行中', value: stats.paid, icon: Ticket, iconClass: 'stat-ticket' },
    { label: '已完成', value: stats.completed, icon: Document, iconClass: 'stat-document' },
    { label: '总消费', value: '¥' + stats.totalAmount.toFixed(2), icon: Money, iconClass: 'stat-money' }
  ]
})

const getStatusName = (status) => {
  const map = { PAID: '已支付', COMPLETED: '已完成', CANCELLED: '已取消', REFUND_PENDING: '退款审核中', REFUNDED: '已退款' }
  return map[status] || status
}

const getStatusTagType = (status) => {
  const map = { PAID: 'success', COMPLETED: 'info', CANCELLED: 'warning', REFUND_PENDING: 'warning', REFUNDED: 'danger' }
  return map[status] || 'info'
}

const getOrderCardClass = (status) => {
  const map = { PAID: 'status-paid', COMPLETED: 'status-completed', CANCELLED: 'status-cancelled', REFUND_PENDING: 'status-refunded', REFUNDED: 'status-refunded' }
  return map[status] || ''
}

const formatOrderId = (id) => {
  return String(id).padStart(8, '0')
}

const formatDateTime = (dateTime) => {
  if (!dateTime) return '—'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false
  }).replace(/\//g, '-')
}

const getProjectIconComponent = (name) => {
  if (name.includes('摩天') || name.includes('观景') || name.includes('高空')) return Monitor
  if (name.includes('过山车') || name.includes('极速') || name.includes('飞车')) return Monitor
  if (name.includes('旋转') || name.includes('木马') || name.includes('转马')) return Ticket
  if (name.includes('水上') || name.includes('漂流') || name.includes('戏水') || name.includes('海盗')) return Location
  return Ticket
}

const getProjectAvatarStyle = (name) => {
  const gradients = {
    default: 'linear-gradient(135deg, #64748b 0%, #475569 100%)',
    ferris: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
    roller: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
    merry: 'linear-gradient(135deg, #f59e0b 0%, #f97316 100%)',
    water: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)'
  }
  if (name.includes('摩天') || name.includes('观景') || name.includes('高空')) return { background: gradients.ferris }
  if (name.includes('过山车') || name.includes('极速') || name.includes('飞车')) return { background: gradients.roller }
  if (name.includes('旋转') || name.includes('木马') || name.includes('转马')) return { background: gradients.merry }
  if (name.includes('水上') || name.includes('漂流') || name.includes('戏水') || name.includes('海盗')) return { background: gradients.water }
  return { background: gradients.default }
}

const loadOrders = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.projectName) params.projectName = searchForm.value.projectName
    if (searchForm.value.status) params.status = searchForm.value.status
    if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
      params.startTime = searchForm.value.dateRange[0] + ' 00:00:00'
      params.endTime = searchForm.value.dateRange[1] + ' 23:59:59'
    }

    const res = await getMyOrders(params)
    if (res.code === 200) {
      const data = res.data || {}
      orders.value = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
      pagination.value.total = data.total || orders.value.length
    }
  } catch (e) {
    ElMessage.error('加载订单失败')
    orders.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  loadOrders()
}

const handleReset = () => {
  searchForm.value = { projectName: '', status: '', dateRange: [] }
  pagination.value.current = 1
  loadOrders()
}

const handlePageChange = (page) => {
  pagination.value.current = page
  loadOrders()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  loadOrders()
}

const handleCancel = async (id) => {
  try {
    const res = await request.put(`/v1/order/${id}/cancel`)
    if (res.code === 200) {
      ElMessage.success('订单已取消')
      loadOrders()
    } else {
      ElMessage.error(res.message || '取消失败')
    }
  } catch (e) {
    ElMessage.error('取消失败')
  }
}

const handleRefund = async (id) => {
  try {
    // 退款需管理员审批：这里只提交申请，审批通过后才退款并恢复库存
    const res = await request.post(`/v1/order/${id}/refund`, {})
    if (res.code === 200) {
      ElMessage.success(res.message || '退款申请已提交，请等待管理员审核')
      loadOrders()
    } else {
      ElMessage.error(res.message || '退款申请失败')
    }
  } catch (e) {
    ElMessage.error('退款失败')
  }
}

const viewDetail = (order) => {
  // 可以跳转详情页或打开抽屉
  ElMessage.info('订单详情功能开发中...')
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
/* ===== 我的订单页面样式 ===== */

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
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

.stat-tickets { background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%); }
.stat-ticket { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.stat-document { background: linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%); }
.stat-money { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }

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

/* 订单卡片 */
.orders-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4);
}

.order-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-normal);
}

.order-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-divider);
}

/* 状态变体 */
.order-card.status-paid {
  border-left: 3px solid var(--color-success);
}

.order-card.status-completed {
  border-left: 3px solid var(--color-info);
}

.order-card.status-cancelled {
  opacity: 0.6;
  background: var(--color-bg);
}

.order-card.status-refunded {
  opacity: 0.7;
  background: var(--color-bg);
}

/* 订单头部 */
.order-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  flex-wrap: wrap;
  gap: var(--space-3);
}

.order-id-group {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.order-id-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.order-id {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  background: var(--color-bg-card);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.status-tag {
  font-weight: 500 !important;
  font-size: 11px !important;
}

.order-time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.time-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.time-value {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* 订单主体 */
.order-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  gap: var(--space-4);
  flex-wrap: wrap;
}

.project-info {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex: 1;
  min-width: 0;
}

.project-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.project-avatar :deep(svg) {
  width: 24px;
  height: 24px;
  color: white;
}

.project-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-meta {
  display: flex;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.meta-item :deep(svg) {
  width: 14px;
  height: 14px;
}

/* 总额 */
.order-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding-left: var(--space-4);
  border-left: 1px solid var(--color-divider);
  min-width: 140px;
}

.total-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.total-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

/* 操作区 */
.order-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  background: var(--color-bg);
  border-top: 1px solid var(--color-divider);
  flex-wrap: wrap;
}

.action-btn {
  height: 36px !important;
  padding: 0 16px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: 6px !important;
  border-radius: var(--radius-md) !important;
  transition: all var(--transition-fast) !important;
}

.action-btn:hover {
  transform: translateY(-1px);
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
  max-width: 300px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
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
.orders-skeleton {
  padding: var(--space-4);
}

.skeleton-order {
  padding: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}

.skeleton-order:last-child {
  border-bottom: none;
}

.skeleton-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.skeleton-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
}

/* 响应式 */
@media (max-width: 768px) {
  .order-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-time {
    align-items: flex-start;
  }

  .order-body {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-total {
    align-items: flex-start;
    border-left: none;
    border-top: 1px solid var(--color-divider);
    padding-left: 0;
    padding-top: var(--space-3);
    width: 100%;
  }

  .order-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>