import request from '@/utils/request'

// 提交VIP申请
export const applyVip = (data) => request.post('/v1/vip/apply', data)

// 我的VIP申请
export const getMyVipApplication = () => request.get('/v1/vip/myApplication')

// 所有VIP申请（管理员）
export const getAllVipApplications = (params) => request.get('/v1/vip/applications', { params })

// 审核VIP申请
export const approveVipApplication = (data) => request.post('/v1/vip/approve', data)