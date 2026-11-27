import request from '@/utils/request'

// 登录
export const login = (data) => request.post('/v1/user/login', data)

// 注册
export const register = (data) => request.post('/v1/user/register', data)

// 登出
export const logout = () => request.post('/v1/user/logout')

// 获取当前用户
export const getCurrentUser = () => request.get('/v1/user/current')

// 获取用户列表
export const getUserList = (params) => request.get('/v1/user/findAll', { params })

// 分页获取用户
export const getUserPage = (params) => request.get('/v1/user/findByPage', { params })

// 添加用户
export const createUser = (data) => request.post('/v1/user', data)

// 修改用户
export const updateUser = (data) => request.put('/v1/user', data)

// 删除用户
export const deleteUser = (id) => request.delete(`/v1/user/${id}`)

// 修改密码
export const changePassword = (data) => request.post('/v1/user/changePassword', data)

// 刷新 Token
export const refreshToken = (data) => request.post('/v1/user/refreshToken', data)