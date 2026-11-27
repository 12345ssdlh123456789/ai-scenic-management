<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">所有订单</h1>
        <p class="page-subtitle">管理后台 - 查看全系统订单数据</p>
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
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="搜索用户..."
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="loadOrders"
          />
        </el-form-item>
        <el-form-item label="项目名">
          <el-input
            v-model="searchForm.projectName"
            placeholder="搜索项目..."
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="loadOrders"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" size="small" style="width: 150px" clearable>
            <el-option label="全部" value="" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="已退款" value="REFUNDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            size="small"
            style="width: 280px"
            :picker-options="pickerOptions"
            placeholder="选择日期"
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

    <!-- 订单表格 -->
    <el-card shadow="never" class="table-card">
      <div v-if="loading" class="table-skeleton">
        <div class="skeleton-row" v-for="i in 5" :key="i">
          <div class="skeleton skeleton-text" style="width: 80px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 140px;"></div>
          <div class="skeleton skeleton-text" style="width: 70px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 80px;"></div>
          <div class="skeleton skeleton-text" style="width: 180px;"></div>
        </div>
      </div>

      <el-table
        v-else
        :data="orders"
        border
        stripe
        :highlight-current-row="true"
        row-key="id"
        class="admin-table"
        :empty-text="emptyText"
      >
        <!-- 订单号列宽：实测单元格内容需 103px + 左右 12px 内边距 ≈ 127px -->
        <el-table-column prop="id" label="订单号" width="132" align="center">
          <template #default="scope">
            <span class="order-id-tag">#{{ String(scope.row.id).padStart(8, '0') }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="用户" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <div class="username-cell">
              <div class="user-avatar-mini" :style="getRoleAvatarStyle(scope.row.userRole)">
                {{ firstLetter(scope.row.username) }}
              </div>
              <span class="username-text">{{ scope.row.username }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip />

        <el-table-column prop="quantity" label="数量" width="80" align="center">
          <template #default="scope">
            <span class="qty-badge">{{ scope.row.quantity }}</span>
          </template>
        </el-table-column>

        <el-table-column label="单价" width="100" align="right">
          <template #default="scope">
            <span class="price-text">¥{{ formatMoney(scope.row.unitPrice) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="总价" width="120" align="right">
          <template #default="scope">
            <span class="total-price-text">¥{{ formatMoney(scope.row.totalPrice) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" effect="dark" size="large">
              {{ getStatusName(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="orderTime" label="下单时间" width="200" align="center">
          <template #default="scope">
            <span class="time-text">{{ formatDateTime(scope.row.orderTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <div v-if="!loading && orders.length === 0" class="empty-state-table">
        <div class="empty-icon">
          <Document />
        </div>
        <p class="empty-title">{{ emptyText }}</p>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && orders.length > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
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
import { getAllOrders } from '@/api/order'
import { formatMoney, firstLetter } from '@/utils/format'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Document, Ticket, CircleCheck, Money
} from '@element-plus/icons-vue'

const orders = ref([])
const loading = ref(false)

const searchForm = ref({
  username: '',
  projectName: '',
  status: '',
  dateRange: []
})

const pagination = ref({ current: 1, size: 20, total: 0 })

const pickerOptions = {
  shortcuts: [
    { text: '今日', value: () => { const d = new Date(); return [d, d] } },
    { text: '最近一周', value: () => { const end = new Date(); const start = new Date(); start.setDate(end.getDate() - 7); return [start, end] } },
    { text: '最近一个月', value: () => { const end = new Date(); const start = new Date(); start.setMonth(end.getMonth() - 1); return [start, end] } }
  ]
}

// 统计
const orderStats = computed(() => {
  const total = orders.value.length
  const paid = orders.value.filter(o => o.status === 'PAID').length
  const completed = orders.value.filter(o => o.status === 'COMPLETED').length
  const amount = orders.value.reduce((sum, o) => sum + parseFloat(o.totalPrice || 0), 0)
  return [
    { label: '全部订单', value: total, icon: Document, iconClass: 'stat-doc' },
    { label: '进行中', value: paid, icon: Ticket, iconClass: 'stat-ticket' },
    { label: '已完成', value: completed, icon: CircleCheck, iconClass: 'stat-check' },
    { label: '总金额', value: '¥' + amount.toFixed(2), icon: Money, iconClass: 'stat-money' }
  ]
})

const getStatusName = (s) => ({ PAID: '已支付', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDED: '已退款' }[s] || s)
const getStatusTagType = (s) => ({ PAID: 'success', COMPLETED: 'info', CANCELLED: 'warning', REFUNDED: 'danger' }[s] || 'info')

const emptyText = computed(() => {
  if (searchForm.value.username || searchForm.value.projectName || searchForm.value.status || searchForm.value.dateRange?.length) {
    return '未找到匹配的订单，请调整筛选条件'
  }
  return '暂无订单数据'
})

const getRoleAvatarStyle = (role) => {
  const map = {
    USER: 'linear-gradient(135deg, #64748b, #475569)',
    VIP: 'linear-gradient(135deg, #f59e0b, #f97316)',
    ADMIN: 'linear-gradient(135deg, #ef4444, #dc2626)',
    SUPER_ADMIN: 'linear-gradient(135deg, #7c3aed, #6d28d9)'
  }
  return { background: map[role] || map.USER }
}

const formatDateTime = (dt) => {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).replace(/\//g, '-')
}

const loadOrders = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.username) params.username = searchForm.value.username
    if (searchForm.value.projectName) params.projectName = searchForm.value.projectName
    if (searchForm.value.status) params.status = searchForm.value.status
    if (searchForm.value.dateRange?.length === 2) {
      params.startTime = searchForm.value.dateRange[0] + ' 00:00:00'
      params.endTime = searchForm.value.dateRange[1] + ' 23:59:59'
    }
    const res = await getAllOrders(params)
    if (res.code === 200) {
      const data = res.data || {}
      orders.value = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
      pagination.value.total = data.total || orders.value.length
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.value.current = 1; loadOrders() }
const handleReset = () => { searchForm.value = { username: '', projectName: '', status: '', dateRange: [] }; pagination.value.current = 1; loadOrders() }
const handlePageChange = (p) => { pagination.value.current = p; loadOrders() }
const handleSizeChange = (s) => { pagination.value.size = s; pagination.value.current = 1; loadOrders() }

onMounted(loadOrders)
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-6); flex-wrap: wrap; gap: var(--space-4); }

.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-4); margin-bottom: var(--space-6); }
@media (max-width: 1024px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 640px) { .stats-grid { grid-template-columns: 1fr; } }

.stat-card { border: 1px solid var(--color-border); border-radius: var(--radius-xl); background: var(--color-bg-card); transition: all var(--transition-normal); }
.stat-card:hover { box-shadow: var(--shadow-md); border-color: var(--color-divider); transform: translateY(-2px); }
.stat-content { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-4); }
.stat-icon { width: 48px; height: 48px; border-radius: var(--radius-lg); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-icon svg { width: 24px; height: 24px; color: white; }
.stat-doc { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
.stat-ticket { background: linear-gradient(135deg, #10b981, #059669); }
.stat-check { background: linear-gradient(135deg, #7c3aed, #6d28d9); }
.stat-money { background: linear-gradient(135deg, #f59e0b, #f97316); }
.stat-value { font-size: 22px; font-weight: 800; color: var(--color-text-primary); margin: 0 0 var(--space-1); }
.stat-label { font-size: 12px; color: var(--color-text-tertiary); margin: 0; text-transform: uppercase; letter-spacing: 0.05em; }

.filter-bar { margin-bottom: var(--space-6); background: var(--color-bg); border: 1px solid var(--color-border-light); }
.filter-form :deep(.el-form-item) { margin-bottom: 0 !important; margin-right: var(--space-4) !important; }
.filter-form :deep(.el-form-item__label) { font-size: 13px !important; font-weight: 500 !important; color: var(--color-text-secondary) !important; padding: 0 !important; line-height: 36px; }
.filter-form :deep(.el-input__inner), .filter-form :deep(.el-select .el-input__inner), .filter-form :deep(.el-date-editor .el-input__inner) { height: 36px !important; font-size: 13px !important; }

.table-card { border-radius: var(--radius-xl); border: 1px solid var(--color-border); overflow: hidden; }
.admin-table :deep(.el-table th) { background: var(--color-bg) !important; color: var(--color-text-secondary) !important; font-weight: 600 !important; font-size: 12px !important; text-transform: uppercase; letter-spacing: 0.05em; }
.admin-table :deep(.el-table td) { font-size: 14px !important; }
.order-id-tag { font-family: 'SF Mono', monospace; font-size: 12px; font-weight: 600; color: var(--color-text-tertiary); background: var(--color-bg); padding: 2px 8px; border-radius: var(--radius-full); }
.username-cell { display: flex; align-items: center; gap: var(--space-2); }
.user-avatar-mini { width: 28px; height: 28px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; flex-shrink: 0; }
.username-text { font-weight: 500; }
.qty-badge { background: var(--color-primary-bg); color: var(--color-primary); padding: 2px 8px; border-radius: var(--radius-full); font-weight: 600; font-size: 13px; }
.price-text { font-family: 'SF Mono', monospace; font-size: 13px; color: var(--color-text-secondary); }
.total-price-text { font-family: 'SF Mono', monospace; font-size: 15px; font-weight: 700; color: var(--color-primary); }
.time-text { font-size: 13px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }

.empty-state-table { padding: var(--space-12) var(--space-6); text-align: center; }
.empty-state-table .empty-icon { width: 80px; height: 80px; border-radius: var(--radius-full); background: var(--color-primary-bg); color: var(--color-primary); display: flex; align-items: center; justify-content: center; margin: 0 auto var(--space-5); font-size: 32px; }
.empty-state-table .empty-title { font-size: 16px; font-weight: 600; color: var(--color-text-secondary); margin: 0; }

.pagination-wrapper { display: flex; justify-content: center; margin-top: var(--space-6); }
.pagination :deep(.el-pager li) { border-radius: var(--radius-md) !important; margin: 0 var(--space-1) !important; }
.pagination :deep(.el-pager li.active) { background: var(--color-primary) !important; border-color: var(--color-primary) !important; }

.table-skeleton { padding: var(--space-4); }
.skeleton-row { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--color-divider); }
.skeleton-row:last-child { border-bottom: none; }

@media (max-width: 768px) {
  .filter-form :deep(.el-form-item) { margin-right: 0 !important; margin-bottom: var(--space-3) !important; width: 100%; }
  .filter-form :deep(.el-input), .filter-form :deep(.el-select), .filter-form :deep(.el-date-editor) { width: 100% !important; }
}
</style>