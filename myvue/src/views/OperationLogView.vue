<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-subtitle">系统审计日志 - 记录所有关键操作</p>
      </div>
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
            @keyup.enter="loadLogs"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-input
            v-model="searchForm.module"
            placeholder="搜索模块..."
            clearable
            size="small"
            style="width: 180px"
            @keyup.enter="loadLogs"
          />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="searchForm.action" placeholder="全部动作" size="small" style="width: 150px" clearable>
            <el-option label="全部" value="" />
            <el-option label="登录" value="登录" />
            <el-option label="登出" value="登出" />
            <el-option label="创建" value="创建" />
            <el-option label="更新" value="更新" />
            <el-option label="删除" value="删除" />
            <el-option label="下架" value="下架" />
            <el-option label="购票" value="购票" />
            <el-option label="取消订单" value="取消订单" />
            <el-option label="退款" value="退款" />
            <el-option label="申请充值" value="申请充值" />
            <el-option label="审批通过" value="审批通过" />
            <el-option label="拒绝" value="拒绝" />
            <el-option label="申请VIP" value="申请VIP" />
            <el-option label="通过VIP" value="通过VIP" />
            <el-option label="修改密码" value="修改密码" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            size="small"
            style="width: 280px"
            :picker-options="pickerOptions"
            placeholder="选择操作时间"
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

    <!-- 日志表格 -->
    <el-card shadow="never" class="table-card">
      <div v-if="loading" class="table-skeleton">
        <div class="skeleton-row" v-for="i in 5" :key="i">
          <div class="skeleton skeleton-text" style="width: 60px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 200px;"></div>
          <div class="skeleton skeleton-text" style="width: 120px;"></div>
          <div class="skeleton skeleton-text" style="width: 80px;"></div>
          <div class="skeleton skeleton-text" style="width: 180px;"></div>
        </div>
      </div>

      <el-table
        v-else
        :data="logs"
        border
        stripe
        :highlight-current-row="true"
        row-key="id"
        class="admin-table"
        :empty-text="emptyText"
      >
        <el-table-column prop="id" label="ID" width="88" align="center">
          <template #default="scope">
            <span class="log-id">{{ scope.row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="操作用户" min-width="170">
          <template #default="scope">
            <div class="username-cell">
              <div class="user-avatar-mini" :style="getRoleAvatarStyle(scope.row.userRole || 'USER')">
                {{ scope.row.username?.charAt(0)?.toUpperCase() || '?' }}
              </div>
              <div>
                <span class="username-text" :title="scope.row.username">{{ scope.row.username || '—' }}</span>
                <span class="user-id" v-if="scope.row.userId">ID: {{ scope.row.userId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="module" label="模块" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" effect="dark" :type="getModuleTagType(scope.row.module)">
              {{ scope.row.module }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="action" label="动作" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" effect="dark" :type="getActionTagType(scope.row.action)">
              {{ scope.row.action }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />

        <!-- IPv6 形态 0:0:0:0:0:0:0:1 实测需 129px 内容宽度 -->
        <el-table-column prop="ipAddress" label="IP 地址" width="176" align="center" class="ip-column">
          <template #default="scope">
            <span class="ip-text" v-if="scope.row.ipAddress">{{ scope.row.ipAddress }}</span>
            <span class="ip-text empty" v-else>—</span>
          </template>
        </el-table-column>

        <!-- 表头"耗时(ms)"需为排序留白，100px 会显示成"耗时(..." -->
        <el-table-column prop="duration" label="耗时(ms)" width="116" align="center">
          <template #default="scope">
            <span class="duration-text" :class="{ 'slow': scope.row.duration && scope.row.duration > 1000 }">
              {{ scope.row.duration ? scope.row.duration + 'ms' : '—' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="operateTime" label="操作时间" width="200" align="center">
          <template #default="scope">
            <span class="time-text">{{ formatDateTime(scope.row.operateTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <div v-if="!loading && logs.length === 0" class="empty-state-table">
        <div class="empty-icon">
          <Document />
        </div>
        <p class="empty-title">{{ emptyText }}</p>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && logs.length > 0" class="pagination-wrapper">
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
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Document } from '@element-plus/icons-vue'

const logs = ref([])
const loading = ref(false)

const searchForm = ref({
  username: '',
  module: '',
  action: '',
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

const getModuleTagType = (module) => {
  const map = {
    '用户管理': 'primary',
    '项目管理': 'success',
    '订单管理': 'info',
    '充值管理': 'warning',
    'VIP管理': 'purple',
    '操作日志': 'gray'
  }
  return map[module] || 'info'
}

const getActionTagType = (action) => {
  const map = {
    '登录': 'success',
    '登出': 'info',
    '创建': 'primary',
    '更新': 'warning',
    '删除': 'danger',
    '下架': 'danger',
    '购票': 'success',
    '取消订单': 'warning',
    '退款': 'danger',
    '申请充值': 'info',
    '审批通过': 'success',
    '拒绝': 'danger',
    '申请VIP': 'primary',
    '通过VIP': 'success',
    '修改密码': 'warning'
  }
  return map[action] || 'info'
}

const emptyText = computed(() => {
  if (searchForm.value.username || searchForm.value.module || searchForm.value.action || searchForm.value.dateRange?.length) {
    return '未找到匹配的日志记录'
  }
  return '暂无操作日志'
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

const loadLogs = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.username) params.username = searchForm.value.username
    if (searchForm.value.module) params.module = searchForm.value.module
    if (searchForm.value.action) params.action = searchForm.value.action
    if (searchForm.value.dateRange?.length === 2) {
      params.startTime = searchForm.value.dateRange[0] + ' 00:00:00'
      params.endTime = searchForm.value.dateRange[1] + ' 23:59:59'
    }
    const res = await request.get('/v1/log/page', { params })
    if (res.code === 200) {
      const data = res.data || {}
      logs.value = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
      pagination.value.total = data.total || logs.value.length
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.value.current = 1; loadLogs() }
const handleReset = () => { searchForm.value = { username: '', module: '', action: '', dateRange: [] }; pagination.value.current = 1; loadLogs() }
const handlePageChange = (p) => { pagination.value.current = p; loadLogs() }
const handleSizeChange = (s) => { pagination.value.size = s; pagination.value.current = 1; loadLogs() }

onMounted(loadLogs)
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-6); flex-wrap: wrap; gap: var(--space-4); }

.filter-bar { margin-bottom: var(--space-6); background: var(--color-bg); border: 1px solid var(--color-border-light); }
.filter-form :deep(.el-form-item) { margin-bottom: 0 !important; margin-right: var(--space-4) !important; }
.filter-form :deep(.el-form-item__label) { font-size: 13px !important; font-weight: 500 !important; color: var(--color-text-secondary) !important; padding: 0 !important; line-height: 36px; }
.filter-form :deep(.el-input__inner), .filter-form :deep(.el-select .el-input__inner), .filter-form :deep(.el-date-editor .el-input__inner) { height: 36px !important; font-size: 13px !important; }

.table-card { border-radius: var(--radius-xl); border: 1px solid var(--color-border); overflow: hidden; }
.admin-table :deep(.el-table th) { background: var(--color-bg) !important; color: var(--color-text-secondary) !important; font-weight: 600 !important; font-size: 12px !important; text-transform: uppercase; letter-spacing: 0.05em; }
.admin-table :deep(.el-table td) { font-size: 14px !important; }

.log-id { font-family: 'SF Mono', monospace; font-size: 12px; font-weight: 600; color: var(--color-text-tertiary); }
.username-cell { display: flex; align-items: center; gap: var(--space-2); min-width: 0; }
.user-avatar-mini { width: 28px; height: 28px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; flex-shrink: 0; }
/* 用户名长度不可控，截断显示，完整值走 title 原生 tooltip */
.username-text { font-weight: 500; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; display: block; }
.user-id { display: block; font-size: 11px; color: var(--color-text-tertiary); margin-top: 2px; }

.ip-text { font-family: 'SF Mono', monospace; font-size: 13px; color: var(--color-text-secondary); }
.ip-text.empty { color: var(--color-text-tertiary); }

.duration-text { font-family: 'SF Mono', monospace; font-size: 13px; font-weight: 500; color: var(--color-text-secondary); }
.duration-text.slow { color: var(--color-warning); font-weight: 600; }

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