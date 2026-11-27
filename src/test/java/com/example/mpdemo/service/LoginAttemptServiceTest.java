package com.example.mpdemo.service;

import com.example.mpdemo.config.ScenicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private LoginAttemptService service;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;

    @BeforeEach
    void setUp() {
        ScenicProperties props = new ScenicProperties();
        props.initDefaults(); // loginMaxAttempts=5, loginLockMinutes=10

        service = new LoginAttemptService();
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ReflectionTestUtils.setField(service, "scenicProperties", props);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
    }

    @Test
    void initial_not_locked() {
        when(valueOps.get("login:lock:newuser")).thenReturn(null);
        assertFalse(service.isLocked("newuser"));
    }

    @Test
    void lock_after_max_attempts() {
        // increment 依次返回 1..5，第 5 次触发锁定
        when(valueOps.increment("login:attempts:testuser"))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            service.loginFailed("testuser");
        }

        // 锁定 key 被写入（TTL 10 分钟）
        verify(valueOps).set(eq("login:lock:testuser"), anyString(), eq(10L), eq(TimeUnit.MINUTES));

        // 锁定后查询应返回已锁定
        when(valueOps.get("login:lock:testuser")).thenReturn("1");
        assertTrue(service.isLocked("testuser"));
    }

    @Test
    void unlock_after_success() {
        when(valueOps.increment("login:attempts:testuser"))
                .thenReturn(1L, 2L, 3L, 4L, 5L);
        for (int i = 0; i < 5; i++) {
            service.loginFailed("testuser");
        }

        service.loginSucceeded("testuser");

        // 失败计数在第 5 次失败时已删除一次，成功时再次删除
        verify(redisTemplate, times(2)).delete("login:attempts:testuser");
        verify(redisTemplate).delete("login:lock:testuser");

        when(valueOps.get("login:lock:testuser")).thenReturn(null);
        assertFalse(service.isLocked("testuser"));
    }

    @Test
    void partial_attempts_not_locked() {
        when(valueOps.increment("login:attempts:testuser"))
                .thenReturn(1L, 2L, 3L);
        for (int i = 0; i < 3; i++) {
            service.loginFailed("testuser");
        }

        when(valueOps.get("login:lock:testuser")).thenReturn(null);
        assertFalse(service.isLocked("testuser"));

        when(valueOps.get("login:attempts:testuser")).thenReturn("3");
        assertEquals(2, service.getRemainingAttempts("testuser"));
    }
}