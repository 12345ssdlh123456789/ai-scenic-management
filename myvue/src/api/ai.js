import request from '@/utils/request'

// AI 问答
//
// ⚠️ 必须单独放宽 timeout。
// 全局 request.js 的超时是 10s，但本接口要调用外部大模型
// （NVIDIA integrate.api.nvidia.com），实测单次往返需 15-25s，
// 高峰期更久。用默认 10s 会频繁出现 "timeout of 10000ms exceeded"——
// 界面上表现为 AI 客服时好时坏，很容易被误判成后端故障。
//
// 后端 AIChatService 的 HttpClient 只设了 connectTimeout(15s)，
// 没有设 readTimeout，因此服务端会一直等模型返回；
// 这里给 90s 作为客户端的最终兜底。
export function chatAI(data) {
    return request.post('/v1/ai/chat', data, { timeout: 90000 })
}

// 获取知识库
export function getKnowledge() {
    return request.get('/v1/ai/knowledge')
}

// 添加知识
export function addKnowledge(data) {
    return request.post('/v1/ai/knowledge', data)
}

// 知识库统计
export const getStats = () => request.get('/v1/ai/stats')