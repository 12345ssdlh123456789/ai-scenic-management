<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">景点项目</h1>
        <p class="page-subtitle">浏览并购买景区门票，管理项目信息</p>
      </div>
      <el-button v-if="isAdmin" type="primary" @click="showAddDialog" class="page-action-btn">
        <Plus /> 新增项目
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-bar">
      <div class="filter-left">
        <el-input
          v-model="searchName"
          placeholder="搜索项目名称..."
          clearable
          size="small"
          style="width: 260px"
          @keyup.enter="loadProjects"
        >
          <template #prefix><Search /></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态筛选" size="small" style="width: 140px" clearable>
          <el-option label="全部" value="" />
          <el-option label="上架中" value="1" />
          <el-option label="已下架" value="0" />
        </el-select>
      </div>
      <div class="filter-right">
        <el-button type="primary" @click="loadProjects" :loading="loading" size="small">
          <Refresh /> 刷新
        </el-button>
      </div>
    </el-card>

    <!-- 加载骨架屏 -->
    <div v-if="loading" class="projects-skeleton">
      <div class="skeleton-card" v-for="i in 6" :key="i">
        <div class="skeleton" style="height: 160px; border-radius: var(--radius-lg) var(--radius-lg) 0 0;"></div>
        <div style="padding: var(--space-4);">
          <div class="skeleton skeleton-text" style="width: 60%; margin-bottom: var(--space-2);"></div>
          <div class="skeleton skeleton-text" style="width: 40%; margin-bottom: var(--space-3);"></div>
          <div class="skeleton skeleton-text" style="width: 30%;"></div>
        </div>
      </div>
    </div>

    <!-- 项目网格 -->
    <div v-else-if="projects.length > 0" class="projects-grid">
      <div
        v-for="project in projects"
        :key="project.id"
        class="project-card"
        :class="{ 'sold-out': project.stock <= 0, 'offline': project.status === 0 }"
      >
        <!-- 图片/图标区 -->
        <div class="project-cover">
          <img
            v-if="project.imageUrl"
            :src="project.imageUrl"
            :alt="project.name"
            class="project-image"
            @error="handleImageError($event, project)"
          />
          <div v-else class="project-icon-large">
            <component :is="getProjectIconComponent(project.name)" />
          </div>

          <!-- 状态标签 -->
          <div class="project-status">
            <el-tag v-if="project.status === 1" :type="project.stock <= 0 ? 'warning' : 'success'" effect="dark" size="small">
              {{ project.stock <= 0 ? '售罄' : '在售' }}
            </el-tag>
            <el-tag v-else type="info" effect="dark" size="small">下架</el-tag>
          </div>

          <!-- VIP 标记 -->
          <el-tag v-if="isVip && project.status === 1" class="vip-tag" effect="dark" size="small">
            <Star /> VIP 8 折
          </el-tag>
        </div>

        <!-- 信息区 -->
        <div class="project-content">
          <h3 class="project-name">{{ project.name }}</h3>
          <p class="project-desc">{{ project.description || '暂无描述' }}</p>

          <div class="project-meta">
            <div class="price-group">
              <span class="price-label">门票价格</span>
              <div class="price-row">
                <span class="price-original" v-if="isVip && project.status === 1">¥{{ project.price }}</span>
                <span class="price-current" :class="{ 'vip-price': isVip && project.status === 1 }">
                  ¥{{ getDisplayPrice(project.price) }}
                </span>
              </div>
            </div>
            <div class="stock-info" :class="{ 'low-stock': project.stock > 0 && project.stock <= 10 }">
              <span class="stock-label">剩余库存</span>
              <span class="stock-value">{{ project.stock }}</span>
            </div>
          </div>
        </div>

        <!-- 操作区 -->
        <div class="project-actions">
          <div v-if="isBuyer && project.status === 1 && project.stock > 0" class="buy-section">
            <div class="qty-selector">
              <el-input-number
                v-model="project.buyQty"
                :min="1"
                :max="project.stock"
                size="small"
                :controls-position="'right'"
                style="width: 100px"
              />
            </div>
            <el-button
              type="primary"
              size="large"
              class="buy-btn"
              @click="handleBuy(project)"
              :loading="buyingId === project.id"
            >
              <ShoppingCart /> 立即购买
            </el-button>
          </div>

          <div v-else-if="isBuyer && project.status === 1 && project.stock <= 0" class="sold-out-section">
            <el-tag type="warning" effect="dark" size="large" disable-transitions>
              <SoldOut /> 已售罄
            </el-tag>
          </div>

          <div v-else-if="project.status === 0" class="offline-section">
            <el-tag type="info" effect="dark" size="large" disable-transitions>
              <Lock /> 已下架
            </el-tag>
          </div>

          <div v-if="isAdmin" class="admin-actions">
            <el-button size="small" type="primary" @click="showEditDialog(project)" class="admin-btn">
              <Edit /> 编辑
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(project.id, project.name)" class="admin-btn" popconfirm popconfirm-title="确定删除该项目吗？" :popconfirm-confirm-button-text="'确定'" :popconfirm-cancel-button-text="'取消'" popconfirm-icon="Warning" popconfirm-icon-color="#ef4444">
              <Delete /> 删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state-full">
      <div class="empty-illustration">
        <Tickets />
      </div>
      <h3 class="empty-title">暂无项目</h3>
      <p class="empty-desc">
        <span v-if="searchName || statusFilter !== ''">尝试调整搜索条件</span>
        <span v-else-if="isAdmin">点击右上角新增第一个项目</span>
        <span v-else>暂无可购买的项目</span>
      </p>
      <el-button v-if="isAdmin && !searchName && statusFilter === ''" type="primary" size="large" @click="showAddDialog">
        <Plus /> 创建项目
      </el-button>
      <el-button v-else-if="searchName || statusFilter !== ''" type="primary" @click="handleReset">
        <Refresh /> 清除筛选
      </el-button>
    </div>
  </div>

  <!-- 新增/编辑对话框 -->
  <el-dialog
    v-model="addDialogVisible"
    :title="editMode ? '编辑项目' : '新增项目'"
    width="600px"
    :close-on-click-modal="false"
    destroy-on-close
    class="project-dialog"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-position="top" class="dialog-form">
      <el-form-item prop="name" label="项目名称">
        <el-input v-model="form.name" placeholder="请输入项目名称（如：过山车、摩天轮）" size="large" clearable maxlength="50" />
      </el-form-item>

      <el-form-item prop="price" label="门票价格（元）">
        <el-input-number
          v-model="form.price"
          :min="0.01"
          :max="99999"
          :precision="2"
          :step="1"
          size="large"
          style="width: 100%"
          placeholder="请输入价格"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item prop="stock" label="库存数量">
        <el-input-number
          v-model="form.stock"
          :min="0"
          :max="99999"
          size="large"
          style="width: 100%"
          placeholder="请输入库存"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item prop="description" label="项目描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          placeholder="请输入项目描述，支持特色介绍、适用人群、注意事项等"
          size="large"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item prop="imageUrl" label="封面图片 URL（可选）">
        <el-input v-model="form.imageUrl" placeholder="https://example.com/image.jpg" size="large" clearable />
        <div v-if="form.imageUrl" class="image-preview">
          <img :src="form.imageUrl" :alt="form.name" @error="form.imageUrl = ''" />
        </div>
      </el-form-item>

      <el-form-item prop="status" label="上架状态" v-if="editMode">
        <el-radio-group v-model="form.status" style="width: 100%">
          <el-radio :value="1" label="上架（在售）" />
          <el-radio :value="0" label="下架（隐藏）" />
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">{{ editMode ? '保存修改' : '创建项目' }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { getProjectList, createProject, updateProject, deleteProject } from '@/api/project'
import { purchaseTicket } from '@/api/order'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, Plus, Edit, Delete, Star,
  ShoppingCart, SoldOut, Lock, Tickets, Monitor,
  Ticket, Location
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const projects = ref([])
const addDialogVisible = ref(false)
const editMode = ref(false)
const searchName = ref('')
const statusFilter = ref('')
const loading = ref(false)
const submitLoading = ref(false)
const buyingId = ref(null)
const formRef = ref(null)

const form = ref({
  id: null,
  name: '',
  price: 0,
  stock: 0,
  description: '',
  imageUrl: '',
  status: 1
})

const currentUser = computed(() => userStore.loginUser)
const isAdmin = computed(() => userStore.isAdmin)
const isBuyer = computed(() => {
  if (!currentUser.value) return false
  return ['USER', 'VIP', 'ADMIN', 'SUPER_ADMIN'].includes(currentUser.value.role)
})
const isVip = computed(() => {
  if (!currentUser.value) return false
  return ['VIP', 'ADMIN', 'SUPER_ADMIN'].includes(currentUser.value.role)
})

const rules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 50, message: '最多 50 字符', trigger: 'blur' }
  ],
  price: [
    { required: true, message: '请输入价格', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '价格必须大于 0', trigger: 'blur' }
  ],
  stock: [
    { required: true, message: '请输入库存', trigger: 'blur' },
    { type: 'number', min: 0, message: '库存不能为负数', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入描述', trigger: 'blur' }
  ],
  imageUrl: [
    { pattern: /^https?:\/\/.+/, message: '请输入有效的 URL 地址', trigger: 'blur' }
  ]
}

// 根据项目名返回图标组件
const getProjectIconComponent = (name) => {
  if (name.includes('摩天') || name.includes('观景') || name.includes('高空')) return Monitor
  if (name.includes('过山车') || name.includes('极速') || name.includes('飞车')) return Monitor
  if (name.includes('旋转') || name.includes('木马') || name.includes('转马')) return Ticket
  if (name.includes('水上') || name.includes('漂流') || name.includes('戏水') || name.includes('海盗')) return Location
  return Ticket
}

const getDisplayPrice = (price) => {
  if (isVip.value) {
    return (price * 0.8).toFixed(2)
  }
  return parseFloat(price).toFixed(2)
}

const loadProjects = async () => {
  loading.value = true
  try {
    const params = {}
    if (searchName.value) params.name = searchName.value
    if (statusFilter.value !== '') params.status = Number(statusFilter.value)

    const res = await getProjectList(params)
    const data = res.data || {}
    const list = Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
    projects.value = list.map(p => ({ ...p, buyQty: 1 }))
  } catch (e) {
    ElMessage.error('加载项目失败')
    projects.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchName.value = ''
  statusFilter.value = ''
  loadProjects()
}

const handleImageError = (event, project) => {
  // 图片加载失败时移除 imageUrl，回退到图标
  const idx = projects.value.findIndex(p => p.id === project.id)
  if (idx > -1) {
    projects.value[idx].imageUrl = ''
  }
}

const showAddDialog = () => {
  editMode.value = false
  form.value = { id: null, name: '', price: 0, stock: 0, description: '', imageUrl: '', status: 1 }
  addDialogVisible.value = true
  nextTick(() => {
    // 聚焦第一个输入框
    const input = document.querySelector('.project-dialog .el-input__inner')
    input?.focus()
  })
}

const showEditDialog = (row) => {
  editMode.value = true
  form.value = {
    id: row.id,
    name: row.name,
    price: row.price,
    stock: row.stock,
    description: row.description || '',
    imageUrl: row.imageUrl || '',
    status: row.status
  }
  addDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return

  submitLoading.value = true
  try {
    let res
    if (editMode.value) {
      res = await updateProject(form.value)
    } else {
      res = await createProject(form.value)
    }

    if (res.code === 200) {
      ElMessage.success(editMode.value ? '更新成功' : '创建成功')
      addDialogVisible.value = false
      loadProjects()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (id, name) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目 "${name}" 吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
    const res = await deleteProject(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadProjects()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    // 用户取消
  }
}

const handleBuy = async (project) => {
  if (buyingId.value) return
  buyingId.value = project.id

  try {
    const res = await purchaseTicket({ projectId: project.id, quantity: project.buyQty })
    if (res.code === 200) {
      ElMessage.success('购票成功！' + (isVip.value ? '（享 8 折优惠）' : ''))
      await loadProjects()
    } else {
      ElMessage.error(res.message || '购票失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '购票失败，请检查余额是否充足')
  } finally {
    buyingId.value = null
  }
}

onMounted(() => {
  userStore.refreshUser()
  loadProjects()
})
</script>

<style scoped>
/* ===== 景点项目页面样式 ===== */

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

/* 筛选栏 */
/* 筛选栏：左右两组在同一行，右侧按钮靠右。
   之前 .el-card__body 不是 flex，导致 .filter-right 被挤到第二行。 */
.filter-bar {
  margin-bottom: var(--space-5);
  background: var(--color-bg-card);
}

.filter-bar :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  padding: var(--space-4) var(--space-5) !important;
}

.filter-left {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  flex-wrap: wrap;
}

.filter-left :deep(.el-input__inner),
.filter-left :deep(.el-select .el-input__inner) {
  height: 36px !important;
  font-size: var(--text-sm) !important;
}

.filter-left :deep(.el-form-item) {
  margin-bottom: 0 !important;
}

.filter-right {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-left: auto;
}

/* 网格布局 */
.projects-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-5);
}

@media (max-width: 1024px) {
  .projects-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}

@media (max-width: 640px) {
  .projects-grid {
    grid-template-columns: 1fr;
  }
}

/* 项目卡片 */
.project-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  transition: all var(--transition-normal);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-sm);
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary);
}

.project-card.sold-out {
  opacity: 0.7;
}

.project-card.offline {
  opacity: 0.5;
  background: var(--color-bg);
}

/* 封面区 */
.project-cover {
  position: relative;
  height: 180px;
  background: var(--color-bg);
  overflow: hidden;
}

.project-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--transition-slow);
}

.project-card:hover .project-image {
  transform: scale(1.05);
}

/* 无图项目的封面。
   种子数据普遍没有 imageUrl，若只放一个居中的图标会显得"图裂了/空着"。
   这里用柔和的斜向渐变 + 细网格纹理 + 一圈光晕，让占位看起来是
   有意为之的设计，而不是加载失败。 */
.project-icon-large {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background:
    radial-gradient(circle at 50% 42%, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0) 62%),
    linear-gradient(135deg, var(--brand-50) 0%, #e0f2fe 55%, var(--info-50) 100%);
  color: var(--brand-500);
}

/* 细网格纹理，增加质感 */
.project-icon-large::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(37, 99, 235, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(37, 99, 235, 0.055) 1px, transparent 1px);
  background-size: 22px 22px;
  -webkit-mask-image: radial-gradient(circle at 50% 50%, #000 20%, transparent 78%);
  mask-image: radial-gradient(circle at 50% 50%, #000 20%, transparent 78%);
  pointer-events: none;
}

.project-icon-large :deep(svg) {
  width: 60px;
  height: 60px;
  position: relative;
  z-index: 1;
  opacity: 0.92;
  filter: drop-shadow(0 4px 10px rgba(37, 99, 235, 0.18));
  transition: transform var(--transition-slow);
}

.project-card:hover .project-icon-large :deep(svg) {
  transform: scale(1.06);
}

/* 状态标签 */
.project-status {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  z-index: 2;
}

.vip-tag {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%) !important;
  color: white !important;
  border: none !important;
  font-weight: 600 !important;
  box-shadow: var(--shadow-sm) !important;
}

.vip-tag :deep(.el-icon) {
  margin-right: 4px;
}

/* 内容区 */
.project-content {
  padding: var(--space-5);
  flex: 1;
  display: flex;
  flex-direction: column;
}

.project-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 var(--space-4);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

/* 价格库存元信息 */
.project-meta {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  margin-top: auto;
}

.price-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.price-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  font-weight: 500;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.price-original {
  font-size: 13px;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

.price-current {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-danger);
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.price-current.vip-price {
  color: #f59e0b;
}

.stock-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
}

.stock-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  font-weight: 500;
}

.stock-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.stock-info.low-stock .stock-value {
  color: var(--color-warning);
}

/* 操作区 */
.project-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-divider);
  background: var(--color-bg);
  flex-wrap: wrap;
}

/* 购买区 */
.buy-section {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
}

.qty-selector :deep(.el-input-number) {
  width: 100px !important;
}

.qty-selector :deep(.el-input-number__input) {
  height: 40px !important;
  font-size: 15px !important;
  text-align: center;
  border-radius: var(--radius-md) !important;
}

.buy-btn {
  flex: 1;
  height: 44px !important;
  font-weight: 600 !important;
  font-size: 15px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: var(--space-2) !important;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.3) !important;
  transition: all var(--transition-fast) !important;
}

.buy-btn:hover:not(:disabled) {
  transform: translateY(-2px) !important;
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.4) !important;
}

.buy-btn:active:not(:disabled) {
  transform: translateY(0) !important;
}

/* 售罄/下架区 */
.sold-out-section,
.offline-section {
  flex: 1;
  display: flex;
  justify-content: center;
}

.sold-out-section :deep(.el-tag),
.offline-section :deep(.el-tag) {
  font-size: 14px !important;
  font-weight: 500 !important;
  height: 40px !important;
  line-height: 36px !important;
  padding: 0 20px !important;
}

/* 管理员操作 */
.admin-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.admin-btn {
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

/* 空状态 */
.empty-state-full {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
  text-align: center;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  margin-top: var(--space-6);
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

/* 对话框 */
.project-dialog :deep(.el-dialog__header) {
  padding: var(--space-5) var(--space-6) !important;
  border-bottom: 1px solid var(--color-divider) !important;
}

.project-dialog :deep(.el-dialog__title) {
  font-size: 18px !important;
  font-weight: 600 !important;
}

.project-dialog :deep(.el-dialog__body) {
  padding: var(--space-6) !important;
}

.project-dialog :deep(.el-dialog__footer) {
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
.dialog-form :deep(.el-input-number__input),
.dialog-form :deep(.el-select .el-input__inner) {
  height: 40px !important;
  font-size: 14px !important;
  border-radius: var(--radius-md) !important;
}

.dialog-form :deep(.el-input-number) {
  width: 100% !important;
}

.dialog-form :deep(.el-input-number__decrease),
.dialog-form :deep(.el-input-number__increase) {
  width: 36px !important;
  border-radius: var(--radius-md) !important;
  border: 1px solid var(--color-border) !important;
  background: var(--color-bg) !important;
  color: var(--color-text-secondary) !important;
}

.image-preview {
  margin-top: var(--space-2);
  width: 120px;
  height: 80px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

/* 骨架屏 */
.projects-skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-5);
}

@media (max-width: 1024px) {
  .projects-skeleton {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}

@media (max-width: 640px) {
  .projects-skeleton {
    grid-template-columns: 1fr;
  }
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-action-btn {
    width: 100%;
    justify-content: center;
  }

  .filter-left {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-left :deep(.el-input),
  .filter-left :deep(.el-select) {
    width: 100% !important;
  }

  .project-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .buy-section {
    flex-direction: column;
  }

  .buy-btn {
    width: 100%;
  }

  .admin-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>