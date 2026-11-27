package com.example.mpdemo.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 幂等性工具
 * 基于 Redis 实现请求去重，防止重复提交（如双击购票）
 *
 * 用法：
 *   if (!idempotencyService.tryAcquire(idempotencyKey)) {
 *       return Result.error("请勿重复提交");
 *   }
 */
@Component
public class IdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long DEFAULT_TTL_SECONDS = 60; // 1 分钟内相同 key 视为重复

    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public IdempotencyService(org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取幂等锁，返回 true 表示允许执行，false 表示重复请求
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_TTL_SECONDS);
    }

    /**
     * 尝试获取幂等锁，自定义 TTL
     */
    public boolean tryAcquire(String key, long ttlSeconds) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        // setIfAbsent = NX，只有 key 不存在时才设置成功
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 主动释放幂等锁（业务失败时调用，允许用户立即重试）
     */
    public void release(String key) {
        if (redisTemplate == null) return;
        redisTemplate.delete(IDEMPOTENCY_PREFIX + key);
    }

    /**
     * 自动生成幂等键（基于 UUID）
     * 调用方应在请求头中传入 Idempotency-Key，此处作为备选
     */
    public static String generateKeyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
