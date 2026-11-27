import request from '@/utils/request'

// 提交充值申请
export const applyRecharge = (data) => request.post('/v1/recharge/apply', data)

// 我的充值记录
export const getMyRecharge = (params) => request.get('/v1/recharge/myApplications', { params })

// 待审核充值（管理员）
export const getPendingRecharge = (params) => request.get('/v1/recharge/pendingApplications', { params })

// 通过充值申请
export const approveRecharge = (id, data) => request.post(`/v1/recharge/approve/${id}`, data)

// 拒绝充值申请
export const rejectRecharge = (id, data) => request.post(`/v1/recharge/reject/${id}`, data)