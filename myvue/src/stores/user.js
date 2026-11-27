import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 安全解析 localStorage 中的用户信息。
 * 直接 JSON.parse 会在数据损坏时抛异常，而这里是在 store 初始化阶段调用的，
 * 一旦抛出会导致整个应用启动失败（白屏且无法恢复）。
 */
function safeParseUser(raw) {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    // 必须是一个带 role 的对象，否则视为无效数据
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (e) {
    console.warn('[userStore] loginUser 数据损坏，已重置:', e)
    localStorage.removeItem('loginUser')
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const loginUser = ref(safeParseUser(localStorage.getItem('loginUser')))

  const isLoggedIn = computed(() => {
    return !!token.value && !!loginUser.value
  })
  const isAdmin = computed(() => {
    if (!loginUser.value) return false
    return loginUser.value.role === 'ADMIN' || loginUser.value.role === 'SUPER_ADMIN'
  })
  const isVip = computed(() => loginUser.value?.role === 'VIP')
  const roleText = computed(() => {
    if (!loginUser.value) return ''
    const map = { USER: '普通用户', VIP: 'VIP用户', ADMIN: '管理员', SUPER_ADMIN: '高级管理员' }
    return map[loginUser.value.role] || ''
  })

  function setLoginData(user, newToken, newRefreshToken) {
    loginUser.value = user
    token.value = newToken
    localStorage.setItem('loginUser', JSON.stringify(user))
    localStorage.setItem('token', newToken)
    if (newRefreshToken) {
      localStorage.setItem('refreshToken', newRefreshToken)
    }
  }

  function clearLoginData() {
    loginUser.value = null
    token.value = ''
    localStorage.removeItem('loginUser')
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  function refreshUser() {
    loginUser.value = safeParseUser(localStorage.getItem('loginUser'))
    token.value = localStorage.getItem('token') || ''
  }

  return { token, loginUser, isLoggedIn, isAdmin, isVip, roleText, setLoginData, clearLoginData, refreshUser }
})