package com.example.mpdemo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Token 黑名单管理
 * 使用 Redis Hash 存储已注销/密码修改后的 token，key 为 token 的 SHA-256 摘要，
 * value 为过期时间戳。每个 token 独立 TTL，避免哈希碰撞导致误杀。
 */
@Component
public class TokenBlacklist {

    private static final String BLACKLIST_HASH_KEY = "token:blacklist";
    private static final long DEFAULT_TTL_MINUTES = 7 * 24 * 60; // 最长 7 天（等于 refresh token 有效期）

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 将 token 加入黑名单
     *
     * @param token      要禁用的 token
     * @param ttlMinutes 过期时间（分钟），过期后自动从黑名单移除。
     *                   当 ttlMinutes <= 0 时直接跳过（避免误删整个黑名单 Hash）
     */
    public void addToBlacklist(String token, long ttlMinutes) {
        if (redisTemplate == null) {
            return;
        }
        if (ttlMinutes <= 0) {
            // token 已过期或 TTL 无效，无需加入黑名单；继续操作会触发 expire(0) 误删整个 Hash
            return;
        }
        String tokenHash = sha256Hex(token);
        // 存为 String 避免 Long/Integer 序列化差异导致的类型转换问题
        String expireAt = String.valueOf(System.currentTimeMillis() + ttlMinutes * 60 * 1000);
        redisTemplate.opsForHash().put(BLACKLIST_HASH_KEY, tokenHash, expireAt);
        // 整个 Hash 共用一个 key TTL。只"延长"不"缩短"：
        // 若按每个 token 的 ttl 直接 expire，短 TTL 的 token 会把整个 Hash（含其他未过期条目）提前删除。
        // 条目级过期由 isBlacklisted/cleanupExpired 懒清理，key 级 TTL 仅作兜底（最长 7 天）。
        Long currentTtl = redisTemplate.getExpire(BLACKLIST_HASH_KEY, TimeUnit.MINUTES);
        long effectiveTtl = Math.max(ttlMinutes,
                currentTtl == null || currentTtl < 0 ? 0 : currentTtl);
        redisTemplate.expire(BLACKLIST_HASH_KEY, effectiveTtl, TimeUnit.MINUTES);
    }

    /**
     * 将 token 加入黑名单（默认 7 天 TTL）
     */
    public void addToBlacklist(String token) {
        addToBlacklist(token, DEFAULT_TTL_MINUTES);
    }

    /**
     * 检查 token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        if (redisTemplate == null) {
            return false;
        }
        String tokenHash = sha256Hex(token);
        Object ttlObj = redisTemplate.opsForHash().get(BLACKLIST_HASH_KEY, tokenHash);
        if (ttlObj == null) {
            return false;
        }
        // 检查是否过期（hash value 统一存为 String 时间戳，避免 Long/Integer 序列化差异）
        long expireAt;
        try {
            expireAt = Long.parseLong(ttlObj.toString());
        } catch (NumberFormatException e) {
            // 格式异常的脏数据直接当成已过期并清理
            redisTemplate.opsForHash().delete(BLACKLIST_HASH_KEY, tokenHash);
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            redisTemplate.opsForHash().delete(BLACKLIST_HASH_KEY, tokenHash);
            return false;
        }
        return true;
    }

    /**
     * 清理过期的黑名单条目（定时任务调用）
     */
    public void cleanupExpired() {
        if (redisTemplate == null) {
            return;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(BLACKLIST_HASH_KEY);
        long now = System.currentTimeMillis();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            long expireAt;
            try {
                expireAt = Long.parseLong(entry.getValue().toString());
            } catch (NumberFormatException e) {
                // 无法解析的脏数据直接清理
                redisTemplate.opsForHash().delete(BLACKLIST_HASH_KEY, entry.getKey());
                continue;
            }
            if (now > expireAt) {
                redisTemplate.opsForHash().delete(BLACKLIST_HASH_KEY, entry.getKey());
            }
        }
    }

    /**
     * 计算 SHA-256 十六进制摘要，避免 hashCode 碰撞
     */
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
            // SHA-256 is guaranteed to be available in all JVMs
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
