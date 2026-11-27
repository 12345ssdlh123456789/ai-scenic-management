package com.example.mpdemo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

import com.example.mpdemo.util.HttpUtil;

/**
 * IP 地址提取过滤器
 * 将所有请求的客户端真实 IP 注入到 request attribute 中，
 * 供后续 HandlerInterceptor / Controller 统一获取，避免重复代码
 */
@Component
public class ClientIpFilter implements Filter {

    private static final String IP_ATTRIBUTE = "clientIp";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ip = HttpUtil.getClientIp(httpRequest);
        httpRequest.setAttribute(IP_ATTRIBUTE, ip);
        chain.doFilter(request, response);
    }

    /**
     * 从 HttpServletRequest 中获取已提取的 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        Object attr = request.getAttribute(IP_ATTRIBUTE);
        if (attr instanceof String) {
            return (String) attr;
        }
        return null;
    }
}
