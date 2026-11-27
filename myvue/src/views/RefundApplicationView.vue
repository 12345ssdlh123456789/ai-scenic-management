<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">退款申请审核</h1>
        <p class="page-subtitle">审批用户的退款申请，通过后退回余额并恢复门票库存</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card shadow="never" class="stat-card" v-for="(stat, index) in refundStats" :key="index">
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
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading" size="small">
            <Search /> 搜索
          </el-button>
          <el-button @click="resetSearch" size="small">
            <Refresh /> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 申请列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="applications" v-loading="loading" stripe style="width: 100%">
        <!-- 90px 时表头"申请ID"会被省略成"申..."（表头需为排序图标留白） -->
        <el-table-column label="申请ID" width="104">
          <template #default="scope">
            <span class="mono">#{{ String(scope.row.id).padStart(6, '0') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="订单ID" width="104">
          <template #default="scope">
            <span class="mono">#{{ String(scope.row.orderId).padStart(6, '0') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="申请人" width="140">
          <template #default="scope">
            <div class="user-cell">
              <div class="avatar">{{ (scope.row.username || '?').charAt(0) }}</div>
              <span>{{ scope.row.username }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="projectName" label="项目" min-width="120" />

        <el-table-column label="数量" width="80" align="center">
          <template #default="scope">
            {{ scope.row.quantity }} 张
          </template>
        </el-table-column>

        <el-table-column label="退款金额" width="120" align="right">
          <template #default="scope">
            <span class="amount">¥{{ Number(scope.row.refundAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="退款理由" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            <span :class="{ 'text-muted': !scope.row.applyReason }">
              {{ scope.row.applyReason || '未填写' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" size="small">
              {{ getStatusName(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="申请时间" width="190">
          <template #default="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right" class="actions-column">
          <template #default="scope">
            <div v-if="scope.row.status === 'PENDING'" class="action-group">
              <el-button
                size="small"
                type="success"
                @click="openReview(scope.row, 'APPROVED')"
                class="action-btn-approve"
              >
                <Check /> 通过
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="openReview(scope.row, 'REJECTED')"
                class="action-btn-reject"
              >
                <Close /> 拒绝
              </el-button>
            </div>
            <div v-else class="reviewed-info">
              <span class="text-muted">{{ scope.row.adminRemark || '已处理' }}</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <component :is="Document" class="empty-icon" />
            <p>暂无退款申请</p>
          </div>
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadApplications"
        />
      </div>
    </el-card>

    <!-- 审批对话框 -->
    <el-dialog
      v-model="reviewDialogVisible"
      :title="reviewAction === 'APPROVED' ? '通过退款申请' : '拒绝退款申请'"
      width="480px"
    >
      <div v-if="currentReview" class="review-summary">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单ID">
            #{{ String(currentReview.orderId).padStart(6, '0') }}
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ currentReview.username }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ currentReview.projectName }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">
            ¥{{ Number(currentReview.refundAmount || 0).toFixed(2) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="reviewAction === 'APPROVED'"
          type="warning"
          :closable="false"
          show-icon
          class="review-alert"
        >
          通过后将立即向用户退还 ¥{{ Number(currentReview.refundAmount || 0).toFixed(2) }}，
          并将 {{ currentReview.quantity }} 张门票的库存加回项目。
        </el-alert>
      </div>

      <el-form :model="reviewForm" :rules="reviewRules" ref="reviewFormRef" label-width="80px">
        <el-form-item label="审核备注" prop="adminRemark">
          <el-input
            v-model="reviewForm.adminRemark"
            type="textarea"
            :rows="3"
            :placeholder="reviewAction === 'APPROVED' ? '选填' : '请说明拒绝原因'"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button
          :type="reviewAction === 'APPROVED' ? 'success' : 'danger'"
          :loading="submitting"
          @click="submitReview"
        >
          确认{{ reviewAction === 'APPROVED' ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getPendingRefunds, approveRefund } from '@/api/order'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Money, Document, Check, Close, Clock
} from '@element-plus/icons-vue'

const applications = ref([])
const loading = ref(false)
const submitting = ref(false)

const searchForm = reactive({ username: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const reviewDialogVisible = ref(false)
const reviewAction = ref('APPROVED')
const currentReview = ref(null)
const reviewFormRef = ref(null)
const reviewForm = reactive({ adminRemark: '' })

const reviewRules = computed(() => ({
  adminRemark: reviewAction.value === 'REJECTED'
    ? [{ required: true, message: '请填写拒绝原因', trigger: 'blur' }]
    : []
}))

const refundStats = computed(() => {
  const total = applications.value.length
  const pending = applications.value.filter(a => a.status === 'PENDING').length
  const approved = applications.value.filter(a => a.status === 'APPROVED').length
  const amount = applications.value
    .filter(a => a.status === 'PENDING')
    .reduce((sum, a) => sum + Number(a.refundAmount || 0), 0)
  return [
    { label: '当前页申请', value: total, icon: Document, iconClass: 'stat-doc' },
    { label: '待审核', value: pending, icon: Clock, iconClass: 'stat-ticket' },
    { label: '已通过', value: approved, icon: Check, iconClass: 'stat-check' },
    { label: '待退金额', value: '¥' + amount.toFixed(2), icon: Money, iconClass: 'stat-money' }
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

const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  return String(dateTime).replace('T', ' ').substring(0, 16)
}

const loadApplications = async () => {
  loading.value = true
  try {
    const res = await getPendingRefunds({
      current: pagination.current,
      size: pagination.size,
      username: searchForm.username || undefined
    })
    if (res.code === 200) {
      applications.value = res.data.records || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载退款申请失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadApplications()
}

const resetSearch = () => {
  searchForm.username = ''
  pagination.current = 1
  loadApplications()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadApplications()
}

const openReview = (row, action) => {
  currentReview.value = row
  reviewAction.value = action
  reviewForm.adminRemark = ''
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (!reviewFormRef.value) return
  try {
    await reviewFormRef.value.validate()
  } catch (e) {
    return
  }

  submitting.value = true
  try {
    const res = await approveRefund({
      applicationId: currentReview.value.id,
      action: reviewAction.value,
      adminRemark: reviewForm.adminRemark
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '审批完成')
      reviewDialogVisible.value = false
      loadApplications()
    } else {
      ElMessage.error(res.message || '审批失败')
    }
  } catch (e) {
    ElMessage.error('审批失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadApplications)
</script>

<style scoped>
.page-container {
  padding: var(--space-6, 24px);
  max-width: 1400px;
  margin: 0 auto;
}

.page-header { margin-bottom: var(--space-5, 20px); }

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary, #1f2329);
  margin: 0 0 4px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--text-secondary, #8a919f);
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card { border-radius: 10px; }

.stat-content { display: flex; align-items: center; gap: 14px; }

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}

.stat-doc { background: linear-gradient(135deg, #5b8def, #3d6fd8); }
.stat-ticket { background: linear-gradient(135deg, #f0a020, #d98a10); }
.stat-check { background: linear-gradient(135deg, #34c77b, #22a463); }
.stat-money { background: linear-gradient(135deg, #e8618c, #d04271); }

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary, #1f2329);
  margin: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary, #8a919f);
  margin: 2px 0 0;
}

.filter-bar { margin-bottom: 16px; border-radius: 10px; }
.filter-form { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }

.table-card { border-radius: 10px; }

.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }

.user-cell { display: flex; align-items: center; gap: 8px; }

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5b8def, #3d6fd8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.amount { font-weight: 600; color: #e8618c; }

.text-muted { color: var(--text-secondary, #8a919f); }

.action-group { display: flex; gap: 6px; justify-content: center; }
.reviewed-info { font-size: 12px; }

.empty-state { padding: 40px 0; text-align: center; color: var(--text-secondary, #8a919f); }
.empty-icon { font-size: 40px; margin-bottom: 10px; opacity: 0.4; }

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.review-summary { margin-bottom: 16px; }
.review-alert { margin-top: 12px; }
</style>
