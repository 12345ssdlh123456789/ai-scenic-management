package com.example.mpdemo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs,
            @Value("${jwt.refresh:604800000}") long refreshMs) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 必须设置且长度不少于32个字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshMs = refreshMs;
    }

    /**
     * 生成访问令牌。
     * <p>
     * 带唯一 {@code jti}：否则同一用户在同一秒内登录两次会得到完全相同的 token，
     * 导致登出时按 token 加黑名单会"连坐"另一个会话，且无法区分会话。
     */
    public String generateToken(Integer userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(userId.toString())
                .claims(Map.of("username", username, "role", role))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 生成刷新令牌（有效期更长）。
     * <p>
     * 必须带唯一 {@code jti}：JWT 的 iat/exp 只有秒级精度，同一秒内为同一用户
     * 连续签发两次会得到完全相同的字符串。这会让 refresh token 轮换失效——
     * 旧 token 被"作废"后立刻又被登记回来，重放依然成功。
     */
    public String generateRefreshToken(Integer userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshMs);

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(userId.toString())
                .claims(Map.of("username", username, "role", role, "type", "refresh"))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 中一次性解析用户信息（避免 JwtAuthenticationFilter 中多次 parseToken）。
     * 抛异常表示 token 非法/过期。
     */
    public Integer getUserIdFromClaims(Claims claims) {
        return Integer.valueOf(claims.getSubject());
    }

    public String getUsernameFromClaims(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    public Integer getUserId(String token) {
        return Integer.valueOf(parseToken(token).getSubject());
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean isTokenExpired(String token) {
        return parseToken(token).getExpiration().before(new Date());
    }

    /**
     * 获取 token 剩余有效时间（分钟）
     * 用于 token 黑名单 TTL 设置，避免黑名单早于 token 本身过期
     */
    public long getRemainingMinutes(String token) {
        try {
            Claims claims = parseToken(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs <= 0) return 0;
            return Math.max(1, remainingMs / 60000); // 至少 1 分钟
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean validateToken(String token) {
        try {
            // 单次解析，避免 isTokenExpired 内部再次调用 parseToken 造成重复解析
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            // 单次解析，避免重复 parseToken
            Claims claims = parseToken(token);
            String type = claims.get("type", String.class);
            return "refresh".equals(type) && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
