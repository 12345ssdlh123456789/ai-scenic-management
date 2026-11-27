import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ProjectView from '../views/ProjectView.vue'
import MyOrderView from '../views/MyOrderView.vue'
import AllOrderView from '../views/AllOrderView.vue'
import UserView from '../views/UserView.vue'
import VipApplyView from '../views/VipApplyView.vue'
import VipApplicationView from '../views/VipApplicationView.vue'
import ProfileView from '../views/ProfileView.vue'
import RechargeHistoryView from '../views/RechargeHistoryView.vue'
import RechargeApplicationView from '../views/RechargeApplicationView.vue'
import AIChatView from '../views/AIChatView.vue'

const routes = [
    { path: '/login', name: 'Login', component: LoginView },
    { path: '/projects', name: 'Projects', component: ProjectView, meta: { requiresAuth: true } },
    { path: '/myOrders', name: 'MyOrders', component: MyOrderView, meta: { requiresAuth: true } },
    { path: '/allOrders', name: 'AllOrders', component: AllOrderView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/users', name: 'Users', component: UserView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/vipApply', name: 'VipApply', component: VipApplyView, meta: { requiresAuth: true, requiresNormalUser: true } },
    { path: '/vipApplications', name: 'VipApplications', component: VipApplicationView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/profile', name: 'Profile', component: ProfileView, meta: { requiresAuth: true } },
    { path: '/rechargeHistory', name: 'RechargeHistory', component: RechargeHistoryView, meta: { requiresAuth: true } },
    { path: '/rechargeApplications', name: 'RechargeApplications', component: RechargeApplicationView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/refundApplications', name: 'RefundApplications', component: () => import('../views/RefundApplicationView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/operationLogs', name: 'OperationLogs', component: () => import('../views/OperationLogView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/aiChat', name: 'AIChat', component: AIChatView, meta: { requiresAuth: true } },
    { path: '/', redirect: '/login' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from) => {
    const token = localStorage.getItem('token')
    const userStr = localStorage.getItem('loginUser')
    const hasCredentials = token && userStr

    // 无凭证访问需认证的页面 → 登录页
    if (!hasCredentials && to.meta.requiresAuth) {
        return '/login'
    }

    // 有凭证访问登录页 → 跳转到项目页
    if (hasCredentials && to.path === '/login') {
        return '/projects'
    }

    // 角色权限检查
    if (hasCredentials) {
        try {
            const user = JSON.parse(userStr)
            if (to.meta.requiresAdmin && user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN') {
                return '/projects'
            }
            if (to.meta.requiresNormalUser && user.role !== 'USER') {
                return '/projects'
            }
        } catch (e) {
            localStorage.clear()
            return '/login'
        }
    }

    return true
})

export default router