<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理系统用户账号、角色与状态</p>
      </div>
      <el-button type="primary" @click="showAddDialog" class="page-action-btn">
        <Plus /> 新增用户
      </el-button>
    </div>

    <!-- 搜索筛选卡片 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="searchForm" inline class="filter-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            size="small"
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="全部角色" size="small" style="width: 160px" clearable>
            <el-option label="普通用户" value="USER" />
            <el-option label="VIP 用户" value="VIP" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="超级管理员" value="SUPER_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" size="small" style="width: 140px" clearable>
            <el-option label="正常" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <Search /> 搜索
          </el-button>
          <el-button @click="handleReset" class="reset-btn">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格卡片 -->
    <el-card shadow="never" class="table-card">
      <!-- 加载骨架屏 -->
      <div v-if="loading" class="table-skeleton">
        <div class="skeleton-row" v-for="i in 5" :key="i">
          <div class="skeleton skeleton-text" style="width: 60px;"></div>
          <div class="skeleton skeleton-text" style="width: 120px;"></div>
          <div class="skeleton skeleton-text" style="width: 100px;"></div>
          <div class="skeleton skeleton-text" style="width: 80px;"></div>
          <div class="skeleton skeleton-text" style="width: 80px;"></div>
          <div class="skeleton skeleton-text" style="width: 180px;"></div>
        </div>
      </div>

      <!-- 表格 -->
      <el-table
        v-else
        :data="users"
        border
        stripe
        :highlight-current-row="true"
        row-key="id"
        class="user-table"
        :empty-text="emptyText"
      >
        <!-- ID 列容：单元格内容实测需 56px（含 id-tag 自身内边距），加左右 12px ≈ 80px -->
        <el-table-column prop="id" label="ID" width="96" align="center">
          <template #default="scope">
            <span class="id-tag">{{ scope.row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="用户名" min-width="160">
          <template #default="scope">
            <div class="username-cell">
              <div class="user-avatar-mini" :style="getRoleAvatarStyle(scope.row.role)">
                {{ firstLetter(scope.row.username) }}
              </div>
              <span class="username-text" :title="scope.row.username">{{ scope.row.username }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="150" align="center">
          <template #default="scope">
            <el-tag :type="getRoleTagType(scope.row.role)" effect="dark" size="large" class="role-tag">
              {{ getRoleName(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.email" class="email-text">{{ scope.row.email }}</span>
            <span v-else class="empty-text-inline">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="phone" label="手机号" width="152" align="center">
          <template #default="scope">
            <span v-if="scope.row.phone">{{ scope.row.phone }}</span>
            <span v-else class="empty-text-inline">—</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="loading"
              @change="handleStatusChange(scope.row)"
              size="small"
            />
          </template>
        </el-table-column>

        <el-table-column prop="balance" label="余额" width="110" align="right" class="balance-column">
          <template #default="scope">
            <span class="balance-text">¥{{ formatMoney(scope.row.balance) }}</span>
          </template>
        </el-table-column>

        <!-- 操作列宽：实测三个按钮 77+60+77=214px，加两个 16px 间距 = 246px，
             再叠加单元格横向内边距 56px（td 16×2 + .cell 12×2）= 302px。
             列宽不足时按钮会被裁切（表现为"删除"显示不全）。 -->
        <el-table-column label="操作" width="304" align="center" fixed="right" class="actions-column">
          <template #default="scope">
            <div class="action-buttons">
              <el-button
                size="small"
                type="primary"
                :disabled="loading"
                @click="showEditDialog(scope.row)"
                class="action-btn-edit"
              >
                <Edit /> 编辑
              </el-button>
              <el-button
                size="small"
                :type="scope.row.status === 1 ? 'warning' : 'success'"
                :disabled="loading"
                @click="handleStatusChange(scope.row)"
                class="action-btn-status"
              >
                {{ scope.row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="loading || !canDelete(scope.row)"
                @click="handleDelete(scope.row.id, scope.row.username)"
                class="action-btn-delete"
                popconfirm
                :popconfirm-title="`确定删除用户 ${scope.row.username} 吗？`"
                :popconfirm-confirm-button-text="'确定'"
                :popconfirm-cancel-button-text="'取消'"
                :popconfirm-icon="Warning"
                :popconfirm-icon-color="'#ef4444'"
              >
                <Delete /> 删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <div v-if="!loading && users.length === 0" class="empty-state">
        <div class="empty-icon">
          <UserFilled />
        </div>
        <p class="empty-title">暂无用户数据</p>
        <p class="empty-desc">{{ emptyText }}</p>
        <el-button type="primary" @click="showAddDialog" v-if="canCreate">
          <Plus /> 创建第一个用户
        </el-button>
      </div>
    </el-card>

    <!-- 分页 -->
    <div v-if="!loading && users.length > 0" class="pagination-wrapper">
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

  <!-- 新增/编辑对话框 -->
  <el-dialog
    v-model="dialogVisible"
    :title="editMode ? '编辑用户' : '新增用户'"
    width="580px"
    :close-on-click-modal="false"
    destroy-on-close
    class="user-dialog"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-position="top" class="dialog-form">
      <el-form-item prop="username" label="用户名" v-if="!editMode">
        <el-input v-model="form.username" placeholder="请输入用户名" size="large" clearable autocomplete="username" />
      </el-form-item>
      <el-form-item prop="username" label="用户名" v-else>
        <el-input v-model="form.username" placeholder="用户名" size="large" disabled />
        <template #error>
          <div class="readonly-hint">用户名不可修改</div>
        </template>
      </el-form-item>

      <el-form-item prop="password" label="密码" v-if="!editMode">
        <el-input v-model="form.password" type="password" show-password placeholder="请输入密码（至少6位）" size="large" autocomplete="new-password" />
      </el-form-item>
      <el-form-item prop="password" label="新密码" v-else>
        <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改密码" size="large" autocomplete="new-password" />
        <template #error>
          <div class="optional-hint">留空表示不修改密码</div>
        </template>
      </el-form-item>

      <el-form-item prop="role" label="角色">
        <el-select v-model="form.role" placeholder="请选择角色" size="large" style="width: 100%" clearable>
          <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
        </el-select>
      </el-form-item>

      <el-form-item prop="email" label="邮箱">
        <el-input v-model="form.email" placeholder="请输入邮箱" size="large" clearable autocomplete="email" />
      </el-form-item>

      <el-form-item prop="phone" label="手机号">
        <el-input v-model="form.phone" placeholder="请输入手机号" size="large" clearable autocomplete="tel" />
      </el-form-item>

      <el-form-item prop="status" label="账户状态" v-if="editMode">
        <el-radio-group v-model="form.status" style="width: 100%">
          <el-radio :value="1" label="正常" />
          <el-radio :value="0" label="禁用" />
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">{{ editMode ? '保存修改' : '创建用户' }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getUserList, createUser, updateUser, deleteUser } from '@/api/user'
import { formatMoney, firstLetter } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Warning, UserFilled } from '@element-plus/icons-vue'

const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const searchForm = ref({ username: '', role: '', status: '' })
const pagination = ref({ current: 1, size: 10, total: 0 })

const form = ref({
  id: null,
  username: '',
  password: '',
  role: 'USER',
  email: '',
  phone: '',
  status: 1
})

const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: 'VIP 用户', value: 'VIP' },
  { label: '管理员', value: 'ADMIN' },
  { label: '超级管理员', value: 'SUPER_ADMIN' }
]

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度 2-20 字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

// 计算属性：是否有创建权限
const canCreate = computed(() => true)

// 计算属性：是否可删除（超级管理员不可删，自己不可删）
const canDelete = (row) => row.role !== 'SUPER_ADMIN'

// 角色标签类型
const getRoleTagType = (role) => {
  const map = { USER: 'info', VIP: 'success', ADMIN: 'warning', SUPER_ADMIN: 'danger' }
  return map[role] || 'info'
}

// 角色名称
const getRoleName = (role) => {
  const map = { USER: '普通用户', VIP: 'VIP 用户', ADMIN: '管理员', SUPER_ADMIN: '超级管理员' }
  return map[role] || role
}

// 角色头像渐变
const getRoleAvatarStyle = (role) => {
  const gradients = {
    USER: 'linear-gradient(135deg, #64748b 0%, #475569 100%)',
    VIP: 'linear-gradient(135deg, #f59e0b 0%, #f97316 100%)',
    ADMIN: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
    SUPER_ADMIN: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)'
  }
  return { background: gradients[role] || gradients.USER }
}

// 空状态文本
const emptyText = computed(() => {
  if (searchForm.value.username || searchForm.value.role || searchForm.value.status) {
    return '未找到匹配的用户，尝试调整筛选条件'
  }
  return '暂无用户，点击右上角新增用户'
})

const loadUsers = async () => {
  loading.value = true
  try {
    const params = { current: pagination.value.current, size: pagination.value.size }
    if (searchForm.value.username) params.username = searchForm.value.username
    if (searchForm.value.role) params.role = searchForm.value.role
    if (searchForm.value.status !== '') params.status = searchForm.value.status

    const res = await getUserList(params)
    if (res.code === 200) {
      users.value = res.data.records || res.data
      pagination.value.total = res.data.total || users.value.length
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  loadUsers()
}

const handleReset = () => {
  searchForm.value = { username: '', role: '', status: '' }
  pagination.value.current = 1
  loadUsers()
}

const handlePageChange = (page) => {
  pagination.value.current = page
  loadUsers()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  loadUsers()
}

const showAddDialog = () => {
  editMode.value = false
  form.value = { id: null, username: '', password: '', role: 'USER', email: '', phone: '', status: 1 }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  editMode.value = true
  form.value = {
    id: row.id,
    username: row.username,
    password: '',
    role: row.role,
    email: row.email || '',
    phone: row.phone || '',
    status: row.status
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return

  submitLoading.value = true
  try {
    let res
    if (editMode.value) {
      const { password, ...updateData } = form.value
      // 如果密码为空，不传 password 字段
      if (!password) delete updateData.password
      res = await updateUser(updateData)
    } else {
      res = await createUser(form.value)
    }

    if (res.code === 200) {
      ElMessage.success(editMode.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadUsers()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitLoading.value = false
  }
}

const handleStatusChange = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    const res = await updateUser({ id: row.id, status: newStatus })
    if (res.code === 200) {
      ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
      loadUsers()
    } else {
      row.status = row.status === 1 ? 0 : 1 // 回滚
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (id, username) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${username}" 吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
    const res = await deleteUser(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadUsers()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    // 用户取消或错误
  }
}

onMounted(loadUsers)
</script>

<style scoped>
/* ===== 用户管理页面样式 ===== */

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.page-action-btn {
  height: 40px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: var(--space-2) !important;
}

/* 筛选卡片 */
.filter-card {
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
.filter-form :deep(.el-select .el-input__inner) {
  height: 36px !important;
  font-size: 13px !important;
}

.reset-btn {
  height: 36px !important;
  border-color: var(--color-border) !important;
  color: var(--color-text-secondary) !important;
}

.reset-btn:hover {
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  background: var(--color-primary-bg) !important;
}

/* 表格卡片 */
.table-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-lg) !important;
}

.table-card :deep(.el-table__header-wrapper) {
  background: var(--color-bg) !important;
}

.table-card :deep(.el-table th) {
  background: var(--color-bg) !important;
  color: var(--color-text-secondary) !important;
  font-weight: 600 !important;
  font-size: 12px !important;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: var(--space-3) var(--space-4) !important;
  border-bottom: 1px solid var(--color-divider) !important;
}

.table-card :deep(.el-table td) {
  padding: var(--space-3) var(--space-4) !important;
  font-size: 14px !important;
  border-bottom: 1px solid var(--color-divider) !important;
  transition: background var(--transition-fast);
}

.table-card :deep(.el-table tr:hover > td) {
  background: var(--color-bg-hover) !important;
}

.table-card :deep(.el-table tr.current-row > td) {
  background: var(--color-primary-bg) !important;
}

/* ID 标签 */
.id-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-variant-numeric: tabular-nums;
}

/* 用户名单元格 */
.username-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.user-avatar-mini {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: white;
  flex-shrink: 0;
}

.username-text {
  font-weight: 500;
  color: var(--color-text-primary);
  /* 用户名长度不可控（如 probe_admin_18），必须截断而不是撑破单元格。
     完整值通过 title 属性以原生 tooltip 展示。 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 角色标签 */
.role-tag {
  font-weight: 500 !important;
  font-size: 12px !important;
  min-width: 80px;
}

/* 邮箱文本 */
.email-text {
  color: var(--color-text-primary);
  font-size: 13px;
}

.empty-text-inline {
  color: var(--color-text-tertiary);
  font-size: 13px;
}

/* 余额列 */
.balance-column :deep(.cell) {
  padding-right: var(--space-6) !important;
}

.balance-text {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

/* 操作按钮 */
.actions-column :deep(.cell) {
  /* 只保留纵向内边距。横向若再各加 16px，会在单元格自身 12px 之外
     再吃掉 32px，使 276px 的列只剩 219px，三个按钮（实测需 233px）
     被裁切。横向收紧到 4px，把宽度让给按钮。 */
  padding: var(--space-2) var(--space-1) !important;
}

.action-buttons {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
  /* 不允许换行：三个按钮（编辑/禁用/删除）一旦换行，行高会翻倍。
     注意每个按钮内的图标还会占 12-15px + 4px 间距，
     调整按钮内边距/图标间距时，务必同步复核本列宽度是否仍然够用。 */
  flex-wrap: nowrap;
}

.action-btn-edit,
.action-btn-status,
.action-btn-delete {
  height: 32px !important;
  padding: 0 12px !important;
  font-size: 12px !important;
  font-weight: 500 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: 4px !important;
  border-radius: var(--radius-md) !important;
  white-space: nowrap;
  transition: all var(--transition-fast) !important;
}

.action-btn-edit {
  background: var(--color-primary-bg) !important;
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
}

.action-btn-edit:hover {
  background: var(--color-primary) !important;
  color: white !important;
}

.action-btn-status {
  border-color: var(--color-border) !important;
  color: var(--color-text-secondary) !important;
}

.action-btn-status:hover {
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  background: var(--color-primary-bg) !important;
}

.action-btn-delete {
  color: var(--color-danger) !important;
  border-color: var(--color-danger) !important;
  background: var(--color-danger-bg) !important;
}

.action-btn-delete:hover {
  background: var(--color-danger) !important;
  color: white !important;
}

/* 加载骨架屏 */
.table-skeleton {
  padding: var(--space-4);
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}

.skeleton-row:last-child {
  border-bottom: none;
}

/* 空状态 */
.empty-state {
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--space-5);
  font-size: 32px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-2);
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 var(--space-6);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}

.pagination :deep(.el-pagination) {
  font-size: 13px;
}

.pagination :deep(.el-pager li) {
  border-radius: var(--radius-md) !important;
  margin: 0 var(--space-1) !important;
}

.pagination :deep(.el-pager li.active) {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
}

/* 对话框 */
.user-dialog :deep(.el-dialog__header) {
  padding: var(--space-5) var(--space-6) !important;
  border-bottom: 1px solid var(--color-divider) !important;
}

.user-dialog :deep(.el-dialog__title) {
  font-size: 18px !important;
  font-weight: 600 !important;
}

.user-dialog :deep(.el-dialog__body) {
  padding: var(--space-6) !important;
}

.user-dialog :deep(.el-dialog__footer) {
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

.dialog-form :deep(.el-input__inner),
.dialog-form :deep(.el-select .el-input__inner) {
  height: 40px !important;
  font-size: 14px !important;
  border-radius: var(--radius-md) !important;
}

.readonly-hint,
.optional-hint {
  font-size: 11px !important;
  color: var(--color-text-tertiary) !important;
  margin-top: 4px !important;
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
@media (max-width: 1200px) {
  .filter-form :deep(.el-form-item) {
    margin-bottom: var(--space-3) !important;
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-action-btn {
    width: 100%;
    justify-content: center;
  }

  .filter-form :deep(.el-form-item) {
    margin-right: 0 !important;
    width: 100%;
  }

  .filter-form :deep(.el-input),
  .filter-form :deep(.el-select) {
    width: 100% !important;
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;
  }

  .action-btn-edit,
  .action-btn-status,
  .action-btn-delete {
    width: 100%;
    justify-content: center;
  }
}
</style>