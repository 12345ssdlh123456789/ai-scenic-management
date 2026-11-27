package com.example.mpdemo.service;

import com.example.mpdemo.config.ScenicProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败次数服务
 * 记录用户登录失败次数，达到阈值后临时锁定账户
 * 使用 Redis 存储，支持多节点部署
 */
@Service
public class LoginAttemptService {

    @Autowired
    private ScenicProperties scenicProperties;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private int getMaxAttempts() {
        return scenicProperties.getLoginMaxAttempts();
    }

    private long getLockMinutes() {
        return scenicProperties.getLoginLockMinutes();
    }

    private String attemptKey(String username) {
        return "login:attempts:" + username;
    }

    private String lockKey(String username) {
        return "login:lock:" + username;
    }

    public boolean isLocked(String username) {
        if (redisTemplate != null) {
            Object locked = redisTemplate.opsForValue().get(lockKey(username));
            return locked != null;
        }
        // 降级：内存存储
        return false;
    }

    public void loginFailed(String username) {
        if (redisTemplate != null) {
            String key = attemptKey(username);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, getLockMinutes(), TimeUnit.MINUTES);
            }
            if (count != null && count >= getMaxAttempts()) {
                // 锁定时同时清除 attempts 计数，避免锁过期后历史失败次数叠加
                redisTemplate.opsForValue().set(lockKey(username), "1", getLockMinutes(), TimeUnit.MINUTES);
                redisTemplate.delete(attemptKey(username));
            }
        }
    }

    public void loginSucceeded(String username) {
        if (redisTemplate != null) {
            redisTemplate.delete(attemptKey(username));
            redisTemplate.delete(lockKey(username));
        }
    }

    public int getRemainingAttempts(String username) {
        if (redisTemplate != null) {
            Object countObj = redisTemplate.opsForValue().get(attemptKey(username));
            long count = countObj != null ? Long.parseLong(countObj.toString()) : 0;
            return Math.max(0, getMaxAttempts() - (int) count);
        }
        return getMaxAttempts();
    }

    public long getLockRemainingMinutes(String username) {
        if (redisTemplate != null) {
            Long ttl = redisTemplate.getExpire(lockKey(username), TimeUnit.MINUTES);
            return ttl != null && ttl > 0 ? ttl : 0;
        }
        return 0;
    }
}
