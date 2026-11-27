<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">VIP 申请审核</h1>
        <p class="page-subtitle">审核用户提交的 VIP 会员申请</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card shadow="never" class="stat-card" v-for="(stat, index) in vipStats" :key="index">
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
        <el-form-item label="申请人">
          <el-input
            v-model="searchForm.username"
            placeholder="搜索用户名..."
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="loadApplications"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" size="small" style="width: 150px" clearable>
            <el-option label="全部" value="" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
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
          <el-button type="primary" @click="handleSearch" :loading="loading" size="small">
            <Search /> 搜索
          </el-button>
          <el-button @click="handleReset" size="small">
            <Refresh /> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 申请列表 -->
    <el-card shadow="never" class="table-card">
      <div v-if="loading" class="table-skeleton">
        <div class="skeleton-row" v-for="i in 5" :key="i">
          <div class="skeleton skeleton-text" style="width: 60px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 200px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 180px;"></div>
          <div class="skeleton skeleton-text" style="width: 180px;"></div>
        </div>
      </div>

      <el-table
        v-else
        :data="applications"
        border
        stripe
        :highlight-current-row="true"
        row-key="id"
        class="admin-table"
        :empty-text="emptyText"
      >
        <!-- 须容纳标签本体 + 内边距，实测单元格内容需 103px，加左右 12px ≈ 127px -->
        <el-table-column prop="id" label="申请单号" width="132" align="center">
          <template #default="scope">
            <span class="order-id-tag">#{{ String(scope.row.id).padStart(8, '0') }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="申请人" min-width="140">
          <template #default="scope">
            <div class="username-cell">
              <div class="user-avatar-mini" :style="getRoleAvatarStyle(scope.row.userRole || 'USER')">
                {{ firstLetter(scope.row.username) }}
              </div>
              <span class="username-text">{{ scope.row.username }}</span>
              <span class="user-id">ID: {{ scope.row.userId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="applyReason" label="申请理由" min-width="250" show-overflow-tooltip>
          <template #default="scope">
            <span class="reason-text">{{ scope.row.applyReason }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="136" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" effect="dark" size="large" class="status-tag">
              {{ getStatusName(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="申请时间" width="200" align="center">
          <template #default="scope">
            <span class="time-text">{{ formatDateTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="updateTime" label="处理时间" width="200" align="center">
          <template #default="scope">
            <span v-if="scope.row.updateTime" class="time-text">{{ formatDateTime(scope.row.updateTime) }}</span>
            <span v-else class="time-text pending-time">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="center" fixed="right" class="actions-column">
          <template #default="scope">
            <div class="action-buttons" v-if="scope.row.status === 'PENDING'">
              <el-button
                size="small"
                type="success"
                @click="handleReview(scope.row, 'APPROVED')"
                class="action-btn-approve"
                :disabled="reviewingId === scope.row.id"
              >
                <Check /> 通过
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="handleReview(scope.row, 'REJECTED')"
                class="action-btn-reject"
                :disabled="reviewingId === scope.row.id"
              >
                <Close /> 拒绝
              </el-button>
            </div>
            <div v-else class="reviewed-info">
              <span class="reviewed-by" v-if="scope.row.adminId">审核人: ID {{ scope.row.adminId }}</span>
              <el-tag v-if="scope.row.adminRemark" size="small" effect="dark" type="info" class="remark-tag">
                {{ scope.row.adminRemark }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <div v-if="!loading && applications.length === 0" class="empty-state-table">
        <div class="empty-icon">
          <Star />
        </div>
        <p class="empty-title">{{ emptyText }}</p>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && applications.length > 0" class="pagination-wrapper">
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

  <!-- 审核对话框 -->
  <el-dialog
    v-model="reviewDialogVisible"
    :title="reviewAction === 'APPROVED' ? '通过 VIP 申请' : '拒绝 VIP 申请'"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    class="review-dialog"
  >
    <div v-if="currentReview" class="review-info">
      <div class="review-applicant">
        <div class="avatar" :style="getRoleAvatarStyle(currentReview.userRole || 'USER')">
          {{ firstLetter(currentReview.username) }}
        </div>
        <div>
          <p class="applicant-name">{{ currentReview.username }}</p>
          <p class="applicant-id">用户 ID: {{ currentReview.userId }}</p>
        </div>
      </div>
      <div class="review-reason">
        <span class="reason-label">申请理由：</span>
        <p class="reason-content">{{ currentReview.applyReason }}</p>
      </div>
    </div>

    <el-divider />

    <el-form :model="reviewForm" :rules="reviewRules" ref="reviewFormRef" label-position="top" class="dialog-form">
      <el-form-item prop="adminRemark" :label="reviewAction === 'APPROVED' ? '审核通过备注' : '拒绝理由'">
        <el-input
          v-model="reviewForm.adminRemark"
          type="textarea"
          :rows="4"
          :placeholder="reviewAction === 'APPROVED' ? '选填：通过备注，如欢迎语、特别说明等' : '必填：请说明拒绝原因'"
          size="large"
          show-word-limit
          maxlength="500"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button
          :type="reviewAction === 'APPROVED' ? 'success' : 'danger'"
          @click="submitReview"
          :loading="reviewLoading"
        >
          {{ reviewAction === 'APPROVED' ? '确认通过' : '确认拒绝' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAllVipApplications } from '@/api/vip'
import request from '@/utils/request'
import { firstLetter } from '@/utils/format'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Star, Clock,
  Check, Close
} from '@element-plus/icons-vue'

const applications = ref([])
const loading = ref(false)
const reviewingId = ref(null)

const searchForm = ref({
  username: '',
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

// 审核对话框状态
const reviewDialogVisible = ref(false)
const reviewLoading = ref(false)
const reviewAction = ref('')
const currentReview = ref(null)
const reviewFormRef = ref(null)
const reviewForm = ref({ adminRemark: '' })

const reviewRules = {
  adminRemark: [
    { required: true, message: '请填写审核意见', trigger: 'blur' },
    { max: 500, message: '最多 500 字符', trigger: 'blur' }
  ]
}

// 统计
const vipStats = computed(() => {
  const total = applications.value.length
  const pending = applications.value.filter(a => a.status === 'PENDING').length
  const approved = applications.value.filter(a => a.status === 'APPROVED').length
  const rejected = applications.value.filter(a => a.status === 'REJECTED').length
  return [
    { label: '全部申请', value: total, icon: Star, iconClass: 'stat-star' },
    { label: '待审核', value: pending, icon: Clock, iconClass: 'stat-clock' },
    { label: '已通过', value: approved, icon: Check, iconClass: 'stat-approved' },
    { label: '已拒绝', value: rejected, icon: Close, iconClass: 'stat-rejected' }
  ]
})

const getStatusName = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }[s] || s)
const getStatusTagType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info')

const emptyText = computed(() => {
  if (searchForm.value.username || searchForm.value.status || searchForm.value.dateRange?.length) {
    return '未找到匹配的申请，请调整筛选条件'
  }
  return '暂无 VIP 申请'
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

const loadApplications = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.username) params.username = searchForm.value.username
    if (searchForm.value.status) params.status = searchForm.value.status
    if (searchForm.value.dateRange?.length === 2) {
      params.startTime = searchForm.value.dateRange[0] + ' 00:00:00'
      params.endTime = searchForm.value.dateRange[1] + ' 23:59:59'
    }
    const res = await getAllVipApplications(params)
    if (res.code === 200) {
      const data = res.data || {}
      applications.value = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
      pagination.value.total = data.total || applications.value.length
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.value.current = 1; loadApplications() }
const handleReset = () => { searchForm.value = { username: '', status: '', dateRange: [] }; pagination.value.current = 1; loadApplications() }
const handlePageChange = (p) => { pagination.value.current = p; loadApplications() }
const handleSizeChange = (s) => { pagination.value.size = s; pagination.value.current = 1; loadApplications() }

const handleReview = (app, action) => {
  reviewAction.value = action
  currentReview.value = app
  reviewForm.value.adminRemark = ''
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (!reviewFormRef.value) return
  const valid = await reviewFormRef.value.validate()
  if (!valid) return

  reviewLoading.value = true
  try {
    const res = await request.post('/v1/vip/approve', {
      applicationId: currentReview.value.id,
      action: reviewAction.value,
      adminRemark: reviewForm.value.adminRemark
    })
    if (res.code === 200) {
      ElMessage.success(reviewAction.value === 'APPROVED' ? '已通过申请' : '已拒绝申请')
      reviewDialogVisible.value = false
      loadApplications()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    reviewLoading.value = false
  }
}

onMounted(loadApplications)
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
.stat-star { background: linear-gradient(135deg, #f59e0b, #f97316); }
.stat-clock { background: linear-gradient(135deg, #f59e0b, #f97316); }
.stat-approved { background: linear-gradient(135deg, #10b981, #059669); }
.stat-rejected { background: linear-gradient(135deg, #ef4444, #dc2626); }
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
.username-cell { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.user-avatar-mini { width: 28px; height: 28px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; flex-shrink: 0; }
.username-text { font-weight: 500; }
.user-id { font-size: 11px; color: var(--color-text-tertiary); }
.reason-text { font-size: 13px; color: var(--color-text-secondary); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.time-text { font-size: 13px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.pending-time { color: var(--color-text-tertiary); font-style: italic; }
.status-tag { font-weight: 500 !important; font-size: 12px !important; min-width: 80px; }

.actions-column :deep(.cell) { padding: var(--space-2) var(--space-4) !important; }
.action-buttons { display: flex; gap: var(--space-2); justify-content: center; }
.action-btn-approve, .action-btn-reject { height: 32px !important; padding: 0 14px !important; font-size: 12px !important; font-weight: 500 !important; display: inline-flex !important; align-items: center !important; gap: 4px !important; border-radius: var(--radius-md) !important; white-space: nowrap; }
.action-btn-approve { background: var(--color-success-bg) !important; border-color: var(--color-success) !important; color: var(--color-success) !important; }
.action-btn-approve:hover { background: var(--color-success) !important; color: white !important; }
.action-btn-reject { background: var(--color-danger-bg) !important; border-color: var(--color-danger) !important; color: var(--color-danger) !important; }
.action-btn-reject:hover { background: var(--color-danger) !important; color: white !important; }

.reviewed-info { display: flex; flex-direction: column; gap: 4px; align-items: center; }
.reviewed-by { font-size: 11px; color: var(--color-text-tertiary); }
.remark-tag { font-size: 11px !important; max-width: 160px; }

.empty-state-table { padding: var(--space-12) var(--space-6); text-align: center; }
.empty-state-table .empty-icon { width: 80px; height: 80px; border-radius: var(--radius-full); background: var(--color-primary-bg); color: var(--color-primary); display: flex; align-items: center; justify-content: center; margin: 0 auto var(--space-5); font-size: 32px; }
.empty-state-table .empty-title { font-size: 16px; font-weight: 600; color: var(--color-text-secondary); margin: 0; }

.pagination-wrapper { display: flex; justify-content: center; margin-top: var(--space-6); }
.pagination :deep(.el-pager li) { border-radius: var(--radius-md) !important; margin: 0 var(--space-1) !important; }
.pagination :deep(.el-pager li.active) { background: var(--color-primary) !important; border-color: var(--color-primary) !important; }

.table-skeleton { padding: var(--space-4); }
.skeleton-row { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--color-divider); }
.skeleton-row:last-child { border-bottom: none; }

/* 审核对话框 */
.review-dialog :deep(.el-dialog__header) { padding: var(--space-5) var(--space-6) !important; border-bottom: 1px solid var(--color-divider) !important; }
.review-dialog :deep(.el-dialog__title) { font-size: 18px !important; font-weight: 600 !important; }
.review-dialog :deep(.el-dialog__body) { padding: var(--space-6) !important; }
.review-dialog :deep(.el-dialog__footer) { padding: var(--space-4) var(--space-6) !important; border-top: 1px solid var(--color-divider) !important; }

.review-info { margin-bottom: var(--space-4); padding: var(--space-4); background: var(--color-bg); border-radius: var(--radius-lg); border: 1px solid var(--color-divider); }
.review-applicant { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-4); padding-bottom: var(--space-4); border-bottom: 1px solid var(--color-divider); }
.review-applicant .avatar { width: 44px; height: 44px; font-size: 18px; }
.applicant-name { font-size: 15px; font-weight: 600; color: var(--color-text-primary); margin: 0 0 2px; }
.applicant-id { font-size: 12px; color: var(--color-text-tertiary); margin: 0; }
.review-reason { background: var(--color-bg-card); padding: var(--space-3); border-radius: var(--radius-md); border: 1px solid var(--color-border); }
.reason-label { font-size: 12px; color: var(--color-text-tertiary); font-weight: 600; display: block; margin-bottom: var(--space-2); }
.reason-content { font-size: 13px; color: var(--color-text-secondary); margin: 0; line-height: 1.6; white-space: pre-wrap; }

.dialog-form :deep(.el-form-item) { margin-bottom: var(--space-5) !important; }
.dialog-form :deep(.el-form-item__label) { font-size: 13px !important; font-weight: 500 !important; color: var(--color-text-secondary) !important; padding: 0 0 var(--space-2) !important; }
.dialog-form :deep(.el-textarea__inner) { font-size: 14px !important; border-radius: var(--radius-md) !important; padding: var(--space-3) !important; min-height: 100px !important; }
.dialog-form :deep(.el-textarea__inner:focus) { border-color: var(--color-primary) !important; box-shadow: 0 0 0 3px var(--color-primary-bg) !important; }

.dialog-footer { display: flex; justify-content: flex-end; gap: var(--space-3); padding-top: var(--space-4); border-top: 1px solid var(--color-divider); margin: 0 calc(-1 * var(--space-6)) calc(-1 * var(--space-6)) !important; padding-left: var(--space-6) !important; padding-right: var(--space-6) !important; padding-bottom: var(--space-6) !important; }

@media (max-width: 768px) {
  .filter-form :deep(.el-form-item) { margin-right: 0 !important; margin-bottom: var(--space-3) !important; width: 100%; }
  .filter-form :deep(.el-input), .filter-form :deep(.el-select), .filter-form :deep(.el-date-editor) { width: 100% !important; }
  .action-buttons { flex-direction: column; align-items: center; }
  .action-btn-approve, .action-btn-reject { width: 100%; justify-content: center; }
}
</style>