package com.example.mpdemo;

import com.example.mpdemo.entity.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 诊断 + 回归测试：验证 @Cacheable 的「写入后能读回」。
 *
 * 背景：曾出现第 1 次请求 200、第 2 次起 500 的现象。
 * 原因是 CacheManager 的 value 序列化器是 GenericJackson2JsonRedisSerializer，
 * 它依赖 JSON 中的 @class 字段还原具体类型；但传入的 ObjectMapper 刻意
 * disable 了 DefaultTyping（防反序列化攻击），于是写入时不含类型信息，
 * 读取时无法还原 -> 反序列化异常 -> 500。
 *
 * 这个测试直接把一个 Result 放进缓存再取出来，能在没有 HTTP 的情况下
 * 精确复现/防止该类问题。
 */
@SpringBootTest
class CacheRoundTripTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void cachedValueCanBeReadBack() {
        Cache cache = cacheManager.getCache("projects");
        assertNotNull(cache, "projects 缓存应存在");

        String key = "roundtrip:test:" + System.nanoTime();
        Result original = Result.success("ok", "hello-cache");

        cache.put(key, original);

        Object readBack;
        try {
            readBack = cache.get(key, Object.class);
        } catch (Exception e) {
            fail("缓存写入后无法读回 —— 序列化器缺少类型信息。异常: " + e, e);
            return;
        }

        assertNotNull(readBack, "缓存应能读回非 null 值");
        assertTrue(readBack instanceof Result,
                "读回的对象类型应为 Result，实际为 " + readBack.getClass().getName());
        assertEquals(original.getCode(), ((Result) readBack).getCode());

        cache.evict(key);
    }

    @Test
    void complexNestedPayloadSurvivesRoundTrip() {
        Cache cache = cacheManager.getCache("projects");
        String key = "roundtrip:nested:" + System.nanoTime();

        // 模拟真实返回：分页对象里嵌 records 列表
        java.util.Map<String, Object> page = new java.util.LinkedHashMap<>();
        page.put("records", java.util.List.of(
                java.util.Map.of("id", 1, "name", "测试", "stock", 10),
                java.util.Map.of("id", 2, "name", "测试2", "stock", 20)));
        page.put("total", 2L);
        Result original = Result.success("ok", page);

        cache.put(key, original);

        Object readBack;
        try {
            readBack = cache.get(key, Object.class);
        } catch (Exception e) {
            fail("嵌套结构缓存读回失败: " + e, e);
            return;
        }
        assertNotNull(readBack);
        cache.evict(key);
    }
}
