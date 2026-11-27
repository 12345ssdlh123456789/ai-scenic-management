<template>
  <div id="app" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
    <el-container v-if="isLoggedIn" class="app-container">
      <el-aside
        :width="isSidebarCollapsed ? sidebarCollapsedWidth : sidebarWidth"
        class="sidebar"
        ref="sidebarRef"
      >
        <div class="sidebar-header">
          <div class="logo-area">
            <div class="logo-icon" :class="{ mini: isSidebarCollapsed }">
              <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="64" height="64" rx="16" fill="currentColor" />
                <path d="M18 22h28M18 32h20M18 42h16" stroke="white" stroke-width="3" stroke-linecap="round" />
              </svg>
            </div>
            <h2 class="logo-text" v-show="!isSidebarCollapsed">景区系统</h2>
          </div>
          <el-button
            class="collapse-btn"
            text
            @click="toggleSidebar"
            :aria-label="isSidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          >
            <el-icon><Expand v-if="isSidebarCollapsed" /><Fold v-else /></el-icon>
          </el-button>
        </div>

        <el-scrollbar class="sidebar-scroll" :wrap-style="{ height: 'calc(100% - 80px)' }">
          <el-menu
            :default-active="activeMenu"
            router
            :collapse="isSidebarCollapsed"
            :collapse-transition="false"
            background-color="transparent"
            text-color="var(--color-text-secondary)"
            active-text-color="var(--color-primary)"
            class="sidebar-menu"
            :props="menuProps"
          >
            <el-menu-item index="/aiChat" :disabled="!hasPermission('aiChat')">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>AI 智能客服</template>
            </el-menu-item>

            <el-menu-item index="/profile" :disabled="!hasPermission('profile')">
              <el-icon><User /></el-icon>
              <template #title>个人中心</template>
            </el-menu-item>

            <el-menu-item index="/projects" :disabled="!hasPermission('projects')">
              <el-icon><Tickets /></el-icon>
              <template #title>景点项目</template>
            </el-menu-item>

            <el-menu-item index="/myOrders" :disabled="!hasPermission('myOrders')">
              <el-icon><List /></el-icon>
              <template #title>我的订单</template>
            </el-menu-item>

            <el-menu-item index="/rechargeHistory" :disabled="!hasPermission('rechargeHistory')">
              <el-icon><Wallet /></el-icon>
              <template #title>充值记录</template>
            </el-menu-item>

            <el-menu-item index="/vipApply" v-if="isUserRole" :disabled="!hasPermission('vipApply')">
              <el-icon><Star /></el-icon>
              <template #title>申请 VIP</template>
            </el-menu-item>

            <el-sub-menu v-if="isAdmin" :index="adminMenuIndex">
              <template #title>
                <el-icon><Management /></el-icon>
                <span>管理中心</span>
              </template>
              <el-menu-item index="/allOrders">
                <el-icon><Document /></el-icon>
                <template #title>所有订单</template>
              </el-menu-item>
              <el-menu-item index="/rechargeApplications">
                <el-icon><CreditCard /></el-icon>
                <template #title>充值审核</template>
              </el-menu-item>
              <el-menu-item index="/refundApplications">
                <el-icon><RefreshLeft /></el-icon>
                <template #title>退款审核</template>
              </el-menu-item>
              <el-menu-item index="/vipApplications">
                <el-icon><CircleCheck /></el-icon>
                <template #title>VIP 审核</template>
              </el-menu-item>
              <el-menu-item index="/users">
                <el-icon><User /></el-icon>
                <template #title>用户管理</template>
              </el-menu-item>
              <el-menu-item index="/operationLogs">
                <el-icon><Clock /></el-icon>
                <template #title>操作日志</template>
              </el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-scrollbar>

        <div class="sidebar-footer" v-if="!isSidebarCollapsed && currentUser">
          <div class="user-info">
            <div class="avatar-wrapper">
              <div class="avatar">{{ currentUser.username?.charAt(0)?.toUpperCase() }}</div>
            </div>
            <div class="user-detail">
              <p class="user-name">{{ currentUser.username }}</p>
              <el-tag :type="roleTagType" size="small" effect="dark">{{ roleText }}</el-tag>
            </div>
          </div>
        </div>
      </el-aside>

      <el-container class="main-container">
        <el-header class="header">
          <div class="header-left">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/projects' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, index) in breadcrumbItems" :key="index" :to="item.path">
                {{ item.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <div class="header-search" v-show="!isSidebarCollapsed">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索菜单、页面..."
                size="small"
                clearable
                :prefix-icon="Search"
                class="search-input"
              />
            </div>

            <el-dropdown trigger="click" placement="bottom-end">
              <el-button class="header-icon-btn" circle size="small" :icon="Bell" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled class="dropdown-header">
                    <span>消息通知</span>
                    <el-tag size="small" type="primary">3</el-tag>
                  </el-dropdown-item>
                  <el-dropdown-item divided>系统维护通知：今晚 22:00-24:00</el-dropdown-item>
                  <el-dropdown-item divided>您的 VIP 申请已通过审核</el-dropdown-item>
                  <el-dropdown-item divided>充值 ¥100 已到账</el-dropdown-item>
                  <el-dropdown-item>
                    <el-button type="text" link class="view-all">查看全部</el-button>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-button class="header-icon-btn" circle size="small" :icon="isFullscreen ? 'Expand' : 'FullScreen'" @click="toggleFullscreen" />

            <el-dropdown trigger="click" placement="bottom-end" hide-on-click>
              <div class="user-dropdown-trigger" @click.stop>
                <div class="avatar avatar-sm">{{ currentUser?.username?.charAt(0)?.toUpperCase() }}</div>
                <span class="username" v-show="!isSidebarCollapsed">{{ currentUser?.username }}</span>
                <ArrowDown v-show="!isSidebarCollapsed" />
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled class="dropdown-user-info">
                    <div class="avatar avatar-md">{{ currentUser?.username?.charAt(0)?.toUpperCase() }}</div>
                    <div>
                      <p class="dropdown-user-name">{{ currentUser?.username }}</p>
                      <p class="dropdown-user-role">{{ roleText }}</p>
                    </div>
                  </el-dropdown-item>
                  <el-dropdown-item divided>
                    <el-icon><User /></el-icon>
                    <span>个人中心</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided v-if="isAdmin">
                    <el-icon><Setting /></el-icon>
                    <span>系统设置</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided>
                    <el-icon><SwitchButton /></el-icon>
                    <span>切换账号</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided class="logout-item" @click="handleLogout">
                    <el-icon><ArrowRight /></el-icon>
                    <span>退出登录</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-button class="header-icon-btn mobile-toggle" circle size="small" :icon="Fold" @click="toggleSidebar" v-show="isMobile" />
          </div>
        </el-header>

        <el-main class="main-content">
          <div class="content-wrapper">
            <transition name="page" mode="out-in">
              <router-view v-slot="{ Component }">
                <component :is="Component" />
              </router-view>
            </transition>
          </div>
        </el-main>
      </el-container>
    </el-container>

    <router-view v-else />

    <div
      class="mobile-overlay"
      v-show="isMobile && isSidebarOpen"
      @click="closeSidebar"
      aria-hidden="true"
    ></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  User, Setting, Bell, ArrowDown, ArrowRight,
  Search, Fold, Expand, SwitchButton,
  ChatDotRound, Tickets, List, Wallet, Star, Management,
  Document, CreditCard, RefreshLeft, CircleCheck, Clock
} from '@element-plus/icons-vue'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sidebarRef = ref(null)

const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUser = computed(() => userStore.loginUser)
const isAdmin = computed(() => userStore.isAdmin)
const isUserRole = computed(() => currentUser.value?.role === 'USER')
const roleText = computed(() => userStore.roleText)
const roleTagType = computed(() => {
  if (!currentUser.value) return 'info'
  const map = { USER: 'info', VIP: 'success', ADMIN: 'warning', SUPER_ADMIN: 'danger' }
  return map[currentUser.value.role] || 'info'
})

const isSidebarCollapsed = ref(false)
const isSidebarOpen = ref(false)
const isMobile = ref(false)
const isFullscreen = ref(false)

const menuProps = {
  expandOnClick: false
}

const breadcrumbItems = computed(() => {
  const path = route.path

  const menuMap = {
    '/aiChat': { title: 'AI 智能客服', path: '/aiChat' },
    '/profile': { title: '个人中心', path: '/profile' },
    '/projects': { title: '景点项目', path: '/projects' },
    '/myOrders': { title: '我的订单', path: '/myOrders' },
    '/rechargeHistory': { title: '充值记录', path: '/rechargeHistory' },
    '/vipApply': { title: '申请 VIP', path: '/vipApply' },
    '/allOrders': { title: '所有订单', path: '/allOrders' },
    '/rechargeApplications': { title: '充值审核', path: '/rechargeApplications' },
    '/refundApplications': { title: '退款审核', path: '/refundApplications' },
    '/vipApplications': { title: 'VIP 审核', path: '/vipApplications' },
    '/users': { title: '用户管理', path: '/users' },
    '/operationLogs': { title: '操作日志', path: '/operationLogs' }
  }

  // 所有页面都返回自己的名称（含 /projects）。
  // 此前 /projects 返回空数组，导致顶栏只剩"首页"，与页面标题"景点项目"重复，
  // 且看不出当前所在位置。
  const matched = Object.entries(menuMap).find(([key]) => path.startsWith(key))
  return matched ? [matched[1]] : []
})

const activeMenu = computed(() => {
  const path = route.path
  const adminPaths = ['/allOrders', '/rechargeApplications', '/refundApplications', '/vipApplications', '/users', '/operationLogs']
  if (adminPaths.some(p => path.startsWith(p))) return '/admin'
  return path
})

const adminMenuIndex = '/admin'

const searchKeyword = ref('')

const hasPermission = (key) => {
  const permissions = {
    aiChat: true,
    profile: true,
    projects: true,
    myOrders: true,
    rechargeHistory: true,
    vipApply: isUserRole.value,
    allOrders: isAdmin.value,
    rechargeApplications: isAdmin.value,
    refundApplications: isAdmin.value,
    vipApplications: isAdmin.value,
    users: isAdmin.value,
    operationLogs: isAdmin.value
  }
  return permissions[key] ?? true
}

const sidebarWidth = 260
const sidebarCollapsedWidth = 72

const toggleSidebar = () => {
  if (isMobile.value) {
    isSidebarOpen.value = !isSidebarOpen.value
  } else {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
  }
}

const closeSidebar = () => {
  isSidebarOpen.value = false
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

const checkMobile = () => {
  isMobile.value = window.innerWidth < 992
  if (isMobile.value) {
    isSidebarCollapsed.value = true
    isSidebarOpen.value = false
  } else {
    isSidebarOpen.value = false
  }
}

const handleLogout = async () => {
  try {
    await request.post('/v1/user/logout')
  } catch (e) {
    console.error('退出失败', e)
  } finally {
    userStore.clearLoginData()
    router.push('/login')
  }
}

watch(() => route.path, () => {
  userStore.refreshUser()
  if (isMobile.value) closeSidebar()
})

const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  document.addEventListener('fullscreenchange', handleFullscreenChange)

  userStore.refreshUser()

  if (userStore.token) {
    try {
      await request.get('/v1/user/current')
    } catch (e) {
      userStore.clearLoginData()
      router.push('/login')
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: relative;
  z-index: var(--z-sticky);
  transition: width var(--transition-normal), box-shadow var(--transition-normal);
}

.sidebar-collapsed .sidebar {
  box-shadow: var(--shadow-md);
}

.sidebar-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--brand-500) 0%, #7c3aed 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 10px -2px rgba(37, 99, 235, 0.45),
              inset 0 1px 0 rgba(255, 255, 255, 0.22);
  transition: all var(--transition-normal);
}

.logo-icon svg {
  width: 20px;
  height: 20px;
  color: white;
}

.logo-icon.mini {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  letter-spacing: var(--tracking-tight);
  transition: opacity var(--transition-fast), width var(--transition-fast);
}

/* 折叠按钮：方形图标按钮，尺寸固定，避免文字撑破圆形 */
.collapse-btn {
  width: 30px !important;
  height: 30px !important;
  min-width: 30px;
  padding: 0 !important;
  border-radius: var(--radius-md) !important;
  color: var(--color-text-tertiary) !important;
  background: transparent !important;
  border: 1px solid transparent !important;
  transition: all var(--transition-fast) !important;
  flex-shrink: 0;
}

.collapse-btn:hover {
  color: var(--brand-600) !important;
  background: var(--brand-50) !important;
  border-color: var(--brand-200) !important;
}

.collapse-btn .el-icon {
  font-size: 16px;
}

.sidebar-scroll {
  flex: 1;
  overflow: hidden;
}

.sidebar-menu {
  border-right: none !important;
  padding: var(--space-3) var(--space-2) var(--space-4);
  background: transparent !important;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 42px !important;
  line-height: 42px !important;
  padding: 0 var(--space-3) !important;
  border-radius: var(--radius-md) !important;
  margin: var(--space-1) var(--space-2) !important;
  transition: background var(--transition-fast), color var(--transition-fast) !important;
  font-weight: 500 !important;
  font-size: var(--text-base) !important;
  color: var(--color-text-secondary) !important;
  position: relative;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: var(--slate-100) !important;
  color: var(--color-text-primary) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--brand-50) !important;
  color: var(--brand-700) !important;
  font-weight: 600 !important;
}

.sidebar-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: var(--radius-full);
  background: linear-gradient(180deg, var(--brand-400), var(--brand-600));
}

.sidebar-menu :deep(.el-menu-item.is-active:hover) {
  background: var(--brand-100) !important;
}

.sidebar-menu :deep(.el-menu-item.is-disabled) {
  opacity: 0.45 !important;
  color: var(--color-text-tertiary) !important;
}

.sidebar-menu :deep(.el-menu-item .el-icon),
.sidebar-menu :deep(.el-sub-menu__title .el-icon) {
  font-size: 17px !important;
  margin-right: var(--space-3) !important;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--brand-700) !important;
  font-weight: 600 !important;
}

.sidebar-menu :deep(.el-sub-menu__icon-arrow) {
  transition: transform var(--transition-fast) !important;
  font-size: 12px !important;
  right: var(--space-3) !important;
}

.sidebar-menu :deep(.el-sub-menu.is-opened .el-sub-menu__icon-arrow) {
  transform: rotate(90deg) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu) {
  background: transparent !important;
  padding-left: var(--space-3) !important;
  position: relative;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  font-size: var(--text-sm) !important;
  height: 38px !important;
  line-height: 38px !important;
  color: var(--color-text-tertiary) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item:hover) {
  color: var(--color-text-primary) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item.is-active) {
  color: var(--brand-700) !important;
}

.sidebar-footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-border-light);
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.avatar-wrapper {
  flex-shrink: 0;
}

.user-detail {
  min-width: 0;
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

#app.sidebar-collapsed .logo-text {
  opacity: 0;
  width: 0;
}

#app.sidebar-collapsed .sidebar-footer {
  display: none;
}

#app.sidebar-collapsed .collapse-btn {
  margin-left: auto;
}

@media (max-width: 991px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    transform: translateX(-100%);
    transition: transform var(--transition-normal), box-shadow var(--transition-normal);
    width: 280px !important;
  }

  .sidebar.open {
    transform: translateX(0);
    box-shadow: var(--shadow-xl);
  }

  #app.sidebar-collapsed .sidebar {
    width: 280px !important;
  }

  .mobile-toggle {
    display: flex !important;
  }

  .mobile-overlay {
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.5);
    z-index: calc(var(--z-sticky) - 1);
    backdrop-filter: blur(2px);
  }
}

.header {
  height: var(--header-height);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  flex-shrink: 0;
}

.header-left {
  flex: 1;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-search {
  width: 260px;
}

.search-input :deep(.el-input__wrapper) {
  height: 36px !important;
  border-radius: var(--radius-full) !important;
  background: var(--slate-100) !important;
  box-shadow: none !important;
  transition: background var(--transition-fast), box-shadow var(--transition-fast) !important;
}

.search-input :deep(.el-input__wrapper:hover) {
  background: var(--slate-100) !important;
  box-shadow: 0 0 0 1px var(--color-border) inset !important;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  background: var(--color-bg-card) !important;
  box-shadow: 0 0 0 1px var(--brand-500) inset,
              0 0 0 4px rgba(37, 99, 235, 0.10) !important;
}

.search-input :deep(.el-input__inner) {
  height: 36px !important;
  font-size: var(--text-sm) !important;
  background: transparent !important;
}

.search-input :deep(.el-input__prefix) {
  color: var(--color-text-tertiary) !important;
  font-size: 15px !important;
}

.header-icon-btn {
  color: var(--color-text-tertiary) !important;
  background: transparent !important;
  border: none !important;
  transition: all var(--transition-fast) !important;
}

.header-icon-btn:hover {
  color: var(--brand-600) !important;
  background: var(--brand-50) !important;
}

.mobile-toggle {
  display: none;
}

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2) var(--space-1) var(--space-1);
  border-radius: var(--radius-full);
  border: 1px solid transparent;
  transition: all var(--transition-fast);
  cursor: pointer;
  margin-left: var(--space-1);
}

.user-dropdown-trigger:hover {
  background: var(--slate-100);
  border-color: var(--color-border);
}

.username {
  font-size: var(--text-sm);
  font-weight: 550;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.dropdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-2) var(--space-3) !important;
  font-weight: 600;
  font-size: var(--text-sm);
  color: var(--color-text-primary);
}

.dropdown-user-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) !important;
  margin-bottom: var(--space-1);
  border-bottom: 1px solid var(--color-border-light);
}

.dropdown-user-name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.dropdown-user-role {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  margin: 2px 0 0;
}

.dropdown-item :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4) !important;
}

.logout-item {
  color: var(--color-danger) !important;
}

.logout-item:hover {
  background: var(--color-danger-bg) !important;
}

.view-all {
  color: var(--color-primary) !important;
  font-weight: 500;
  padding: var(--space-2) var(--space-4) !important;
}

.main-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  background: var(--color-bg);
  overflow: auto;
  padding: 0;
}

.content-wrapper {
  max-width: var(--content-max-width);
  margin: 0 auto;
  width: 100%;
}

.page-enter-active,
.page-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.main-content::-webkit-scrollbar {
  width: 10px;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}

.main-content::-webkit-scrollbar-thumb {
  background: var(--slate-300);
  border-radius: var(--radius-full);
  border: 3px solid var(--color-bg);
}

.main-content::-webkit-scrollbar-thumb:hover {
  background: var(--slate-400);
}

@media (max-width: 1199px) {
  .header-search {
    width: 200px;
  }
}

@media (max-width: 991px) {
  .header-search {
    display: none;
  }

  .mobile-toggle {
    display: flex !important;
  }

  .username {
    display: none;
  }

  .main-content {
    padding: var(--space-4);
  }
}

@media (max-width: 767px) {
  .header {
    padding: 0 var(--space-4);
  }

  .header-right {
    gap: var(--space-1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sidebar,
  .logo-icon,
  .logo-text,
  .collapse-btn,
  .sidebar-menu :deep(.el-menu-item),
  .sidebar-menu :deep(.el-sub-menu__title),
  .header-icon-btn,
  .user-dropdown-trigger,
  .page-enter-active,
  .page-leave-active {
    transition: none !important;
    animation: none !important;
  }
}
</style>