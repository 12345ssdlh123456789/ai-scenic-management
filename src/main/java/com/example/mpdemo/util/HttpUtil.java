package com.example.mpdemo.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求工具类
 */
public final class HttpUtil {

    private HttpUtil() {
        // 防止实例化
    }

    /**
     * 从 HttpServletRequest 中提取客户端真实 IP
     * 优先级：request attribute(clientIp) > X-Forwarded-For > Proxy-Client-IP > WL-Proxy-Client-IP > X-Real-IP > remoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        // 优先从 Filter 设置的 attribute 中获取
        String ip = (String) request.getAttribute("clientIp");
        if (ip != null) {
            return ip;
        }
        // 降级：直接从 header 获取
        ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个代理 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
