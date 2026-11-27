import request from '@/utils/request'

// 购票
export const purchaseTicket = (data) => request.post('/v1/order/purchase', data)

// 我的订单
export const getMyOrders = (params) => request.get('/v1/order/myOrders', { params })

// 所有订单（管理员）
export const getAllOrders = (params) => request.get('/v1/order/allOrders', { params })

// 订单详情
export const getOrderDetail = (id) => request.get(`/v1/order/${id}`)

// 取消订单
export const cancelOrder = (id) => request.put(`/v1/order/${id}/cancel`)

// 申请退款（需管理员审批通过后才退款并恢复库存）
export const applyRefund = (id, data) => request.post(`/v1/order/${id}/refund`, data)

// 我的退款申请
export const getMyRefunds = (params) => request.get('/v1/order/myRefunds', { params })

// 待审批退款申请（管理员）
export const getPendingRefunds = (params) => request.get('/v1/order/pendingRefunds', { params })

// 审批退款申请（管理员）
export const approveRefund = (data) => request.post('/v1/order/approveRefund', data)