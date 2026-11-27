import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

let isRefreshing = false
let pendingRequests = []

const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090',
    timeout: 10000,
    withCredentials: true,
})

// 请求拦截器：直接从 localStorage 读取 token，不依赖 Pinia 状态
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`
        }
        return config
    },
    error => Promise.reject(error)
)

// 刷新 Token - 使用同一个 request 实例，复用拦截器逻辑。
// 后端现在做 refresh token 轮换，返回 { token, refreshToken }，两个都要更新。
async function refreshToken() {
    const res = await request.post('/v1/user/refreshToken', {
        refreshToken: localStorage.getItem('refreshToken')
    })
    if (res.code === 200 && res.data) {
        const newToken = res.data.token
        const newRefreshToken = res.data.refreshToken
        const userStore = useUserStore()
        userStore.setLoginData(userStore.loginUser, newToken, newRefreshToken)
        return newToken
    }
    throw new Error('refresh failed')
}

// 响应拦截器
request.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            console.error('[HTTP Error]', res.code, res.message, response.config.url)
            if (res.code === 401) {
                handleUnauthorized()
            }
            return Promise.reject(new Error(res.message || '请求失败'))
        }
        return res
    },
    error => {
        if (error.response) {
            const message = error.response.data?.message || `服务器错误 (${error.response.status})`
            console.error('[XHR Error]', error.response.status, error.response.config?.url, message)

            if (error.response.status === 401 && !error.config._retry) {
                const userStore = useUserStore()
                if (isRefreshing) {
                    return new Promise((resolve) => {
                        pendingRequests.push((newToken) => {
                            if (newToken) {
                                error.config.headers['Authorization'] = `Bearer ${newToken}`
                            }
                            resolve(newToken ? request(error.config) : Promise.reject(new Error('Token refresh failed')))
                        })
                    })
                }

                error.config._retry = true
                isRefreshing = true

                return refreshToken().then(newToken => {
                    error.config.headers['Authorization'] = `Bearer ${newToken}`
                    return request(error.config)
                }).catch(() => {
                    pendingRequests.forEach(cb => cb(null))
                    pendingRequests = []
                    handleUnauthorized()
                    return Promise.reject(error)
                }).finally(() => {
                    isRefreshing = false
                    pendingRequests.forEach(cb => cb(userStore.token || ''))
                    pendingRequests = []
                })
            }
        } else if (error.code === 'ECONNABORTED') {
            ElMessage.error('请求超时，请稍后重试')
        } else if (!error.response) {
            ElMessage.error('网络异常，请检查后端服务')
        }
        return Promise.reject(error)
    }
)

function handleUnauthorized() {
    const userStore = useUserStore()
    userStore.clearLoginData()
    router.push('/login')
}

export default request
