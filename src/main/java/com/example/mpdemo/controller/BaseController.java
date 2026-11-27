package com.example.mpdemo.controller;

import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * Controller 基类，提供统一的 JWT 信息提取和 IP 获取方法
 */
public abstract class BaseController {

    /**
     * 安全地获取当前用户的 JWT 详细信息
     */
    protected JwtDetails getJwtDetails() {
        try {
            return JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全地获取当前用户的角色 authority（如 "ROLE_ADMIN"）。
     * <p>
     * 优先从 SecurityContext 的 JwtDetails 中读取角色（避免遍历 authorities），
     * 降级方案：在 authorities 中找最高级别角色（ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_VIP > ROLE_USER），
     * 避免使用 {@code iterator().next()} 在多角色场景下随机取值。
     */
    protected String getCurrentRole() {
        try {
            JwtDetails details = getJwtDetails();
            if (details != null && details.getRole() != null) {
                return "ROLE_" + details.getRole();
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return null;
            }
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities == null || authorities.isEmpty()) {
                return null;
            }
            // 优先级：SUPER_ADMIN > ADMIN > VIP > USER
            String[] priority = {"ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_VIP", "ROLE_USER"};
            for (String p : priority) {
                for (GrantedAuthority a : authorities) {
                    if (p.equals(a.getAuthority())) {
                        return p;
                    }
                }
            }
            return authorities.iterator().next().getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取客户端 IP（优先从 Filter attribute 读取，降级从 header 读取）
     */
    protected String getClientIp(HttpServletRequest request) {
        return com.example.mpdemo.util.HttpUtil.getClientIp(request);
    }

    /**
     * 记录操作日志
     */
    protected void logOperation(JwtDetails details, OperationLogService operationLogService,
                                HttpServletRequest request, String module, String action,
                                String description, String requestMethod) {
        if (details != null) {
            operationLogService.log(details.getUserId(), details.getUsername(),
                    module, action, description, requestMethod, null, "success",
                    getClientIp(request), null);
        }
    }
}
