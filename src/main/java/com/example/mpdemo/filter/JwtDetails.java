package com.example.mpdemo.filter;

import org.springframework.security.core.Authentication;

/**
 * 包装 SecurityContext 中的额外 JWT 信息
 */
public class JwtDetails {

    private final Integer userId;
    private final String username;
    private final String role;

    public JwtDetails(Integer userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }

    public static JwtDetails from(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object details = auth.getDetails();
        if (details instanceof JwtDetails jd) {
            return jd;
        }
        return null;
    }
}
