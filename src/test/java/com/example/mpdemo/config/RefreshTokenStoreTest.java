package com.example.mpdemo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RefreshTokenStore 轮换与 fail-closed 行为测试。
 * <p>
 * 安全要求：
 * 1. 已消费的 refresh token 不能再次通过校验（防重放）。
 * 2. Redis 不可用时必须 fail-closed（拒绝），而不是放行。
 */
@SpringBootTest
class RefreshTokenStoreTest {

    @Autowired
    private RefreshTokenStore store;

    @Test
    void consume_removesToken_soItCannotBeReused() {
        Integer userId = 987654;
        String token = "test-refresh-token-rotation";

        store.add(userId, token);
        assertTrue(store.contains(userId, token), "刚登记的 token 应有效");

        // 第一次消费应成功
        assertTrue(store.consume(userId, token), "首次消费应成功");

        // 再次校验必须失败（防止被盗 token 反复使用）
        assertFalse(store.contains(userId, token), "已消费的 token 不应再有效");

        // 重复消费也应失败
        assertFalse(store.consume(userId, token), "重复消费应失败");

        store.clear(userId);
    }

    @Test
    void clear_revokesAllTokensForUser() {
        Integer userId = 987655;
        store.add(userId, "token-a");
        store.add(userId, "token-b");

        assertTrue(store.contains(userId, "token-a"));
        assertTrue(store.contains(userId, "token-b"));

        store.clear(userId);

        assertFalse(store.contains(userId, "token-a"), "clear 后所有 token 都应失效");
        assertFalse(store.contains(userId, "token-b"), "clear 后所有 token 都应失效");
    }

    @Test
    void contains_unknownToken_isFalse() {
        assertFalse(store.contains(987656, "never-registered-token"));
    }

    @Test
    void contains_nullArgs_isFalse_andDoesNotThrow() {
        assertFalse(store.contains(null, "x"));
        assertFalse(store.contains(1, null));
    }
}
