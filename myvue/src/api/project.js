import request from '@/utils/request'

// 获取景点列表（公开）
export const getProjectList = (params) => request.get('/v1/project/list', { params })

// 获取所有景点（管理员）
export const getAllProjects = (params) => request.get('/v1/project/all', { params })

// 获取景点详情
export const getProjectDetail = (id) => request.get(`/v1/project/${id}`)

// 添加景点
export const createProject = (data) => request.post('/v1/project', data)

// 修改景点
export const updateProject = (data) => request.put('/v1/project', data)

// 删除景点
export const deleteProject = (id) => request.delete(`/v1/project/${id}`)