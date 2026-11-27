package com.example.mpdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 已签发 refresh token 存储（按用户维度）
 * <p>
 * 解决"登出后 refresh token 仍可换取新 access token"的问题：
 * 登出 / 修改密码时清空该用户所有已签发的 refresh token，
 * 之后携带旧 refresh token 刷新会被拒绝。
 * <p>
 * Redis 不可用时降级为不校验（与项目整体降级策略一致），保证服务可用。
 */
@Slf4j
@Component
public class RefreshTokenStore {

    private static final String PREFIX = "auth:refresh:";
    /** 与 JWT refresh token 有效期保持一致（7 天） */
    private static final long TTL_DAYS = 7;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 登录成功时登记该用户的 refresh token（SHA-256 摘要，不存明文）
     */
    public void add(Integer userId, String refreshToken) {
        if (redisTemplate == null || userId == null || refreshToken == null) {
            return;
        }
        String key = PREFIX + userId;
        redisTemplate.opsForSet().add(key, sha256Hex(refreshToken));
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 刷新时校验 refresh token 是否仍有效。
     * <p>
     * <b>安全策略：fail-closed。</b>Redis 不可用时返回 {@code false}（拒绝刷新），
     * 而不是放行。这是一个安全控制点，fail-open 会让"登出/改密吊销 refresh token"
     * 在 Redis 故障期间完全失效——攻击者只需等待一次 Redis 波动即可继续使用旧凭证。
     * <p>
     * 代价：Redis 故障期间用户无法刷新 access token（需重新登录一次），
     * 但已签发的 access token 在有效期内仍可正常使用，业务不中断。
     *
     * @return true=有效；Redis 不可用或 token 未登记时返回 false
     */
    public boolean contains(Integer userId, String refreshToken) {
        if (redisTemplate == null) {
            log.warn("Redis 不可用，refresh token 校验失败（fail-closed，拒绝刷新）。"
                    + "用户需重新登录。userId={}", userId);
            return false;
        }
        if (userId == null || refreshToken == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(PREFIX + userId, sha256Hex(refreshToken)));
    }

    /**
     * 消费（作废）一个已使用的 refresh token —— 用于轮换。
     * <p>
     * 每次成功刷新后调用，使旧 refresh token 立即失效，防止被盗的
     * refresh token 在有效期内被重复使用。
     *
     * @return true=成功移除（该 token 之前确实存在）
     */
    public boolean consume(Integer userId, String refreshToken) {
        if (redisTemplate == null || userId == null || refreshToken == null) {
            return false;
        }
        Long removed = redisTemplate.opsForSet().remove(PREFIX + userId, sha256Hex(refreshToken));
        return removed != null && removed > 0;
    }

    /**
     * 注销某个用户所有已签发的 refresh token（登出 / 修改密码时调用）
     */
    public void clear(Integer userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        redisTemplate.delete(PREFIX + userId);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}