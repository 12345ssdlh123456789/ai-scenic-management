package com.example.mpdemo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 限流器
 * 基于滑动窗口计数实现接口访问频率限制
 * 当 Redis 不可用时降级为本地内存限流，保证服务可用性
 *
 * <h3>多节点部署注意</h3>
 * <ul>
 *   <li><b>Redis 可用</b>：所有节点共享限流计数，多节点下总额流 = maxCount（推荐）</li>
 *   <li><b>Redis 不可用降级为本地内存</b>：每个节点独立计数，N 个节点部署时
 *       <b>总限流上限变成 N × maxCount</b>（即限流强度被削弱 N 倍）。
 *       仅作"Redis 故障期间防止服务完全失守"的兜底，<b>不应作为常规多节点部署方案</b>。</li>
 * </ul>
 */
@Component
public class RateLimiter {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 降级：内存限流缓存（仅当 Redis 不可用时使用） */
    private final ConcurrentHashMap<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    /**
     * 尝试获取许可
     *
     * @param key      限流键（如 "rate:login:192.168.1.1"）
     * @param maxCount 最大允许次数
     * @param period   时间窗口（秒）
     * @return true=允许，false=触发限流
     */
    public boolean tryAcquire(String key, int maxCount, long period) {
        if (redisTemplate != null) {
            return tryAcquireRedis(key, maxCount, period);
        }
        return tryAcquireLocal(key, maxCount, period);
    }

    /** Redis 限流实现 */
    private boolean tryAcquireRedis(String key, int maxCount, long period) {
        String redisKey = "rate:" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, period, TimeUnit.SECONDS);
        }
        return count != null && count <= maxCount;
    }

    /** 降级：本地内存限流实现 */
    private boolean tryAcquireLocal(String key, int maxCount, long period) {
        long now = System.currentTimeMillis();
        LocalCounter counter = localCounters.get(key);
        if (counter == null || now > counter.expiry) {
            counter = new LocalCounter(1, now + period * 1000);
            localCounters.put(key, counter);
            return true;
        }
        counter.count++;
        return counter.count <= maxCount;
    }

    private static class LocalCounter {
        long count;
        final long expiry;
        LocalCounter(long count, long expiry) { this.count = count; this.expiry = expiry; }
    }

    /**
     * 获取剩余可用次数
     */
    public long getRemaining(String key, int maxCount) {
        if (redisTemplate != null) {
            String redisKey = "rate:" + key;
            Object val = redisTemplate.opsForValue().get(redisKey);
            long current = val != null ? Long.parseLong(val.toString()) : 0;
            return Math.max(0, maxCount - current);
        }
        LocalCounter count = localCounters.get(key);
        return Math.max(0, maxCount - (count != null ? (int) count.count : 0));
    }
}
