package com.example.mpdemo.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 * - 提供 RedisTemplate 用于编程式操作
 * - 提供 CacheManager 用于 @Cacheable 注解驱动缓存
 * <p>
 * ⚠️ <b>核心约束：两条路径的 value 序列化器必须一致，且都必须带类型信息
 * （{@code @class}）。</b>
 * 本类曾出现"只修了 CacheManager、漏了 RedisTemplate"的问题：
 * CacheManager 用 typedObjectMapper（正确），而 RedisTemplate 用
 * safeObjectMapper（关闭 DefaultTyping），导致 AIChatService 读回
 * {@code LinkedHashMap} 并抛 {@code ClassCastException}，AI 客服永久不可用。
 * 详见 {@link #redisTemplate(RedisConnectionFactory)} 的注释。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * RedisTemplate 配置：key 用 String 序列化，value 用 JSON 序列化。
     * <p>
     * <b>value 序列化器必须使用 {@link #typedObjectMapper()}，不能用
     * {@link #safeObjectMapper()}。</b>
     * <p>
     * 这里曾使用关闭 DefaultTyping 的 safeObjectMapper，导致写入 Redis 的 JSON
     * 不含 {@code @class}。读取时拿到的 List 元素实际是 {@code LinkedHashMap}，
     * 而 {@code AIChatService.loadKnowledge()} 里
     * {@code (List<KnowledgeEntry>) cached} 是<b>泛型强转，运行时会被擦除</b>，
     * 因而不会在此处报错——直到元素被当作 {@code KnowledgeEntry} 使用
     * （{@code AIChatService.retrieve()} 调用 {@code calculateSimilarity}）才抛：
     * <pre>
     * java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to
     *   class com.example.mpdemo.entity.KnowledgeEntry
     * </pre>
     * 表现为 AI 客服永久返回"系统繁忙"。
     * <p>
     * 与 cacheManager 的 value 序列化器保持一致（同样基于白名单的
     * DefaultTyping），既恢复类型还原，又不引入任意类实例化的攻击面。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 序列化（JSON）- 必须带类型信息，否则读回退化成 LinkedHashMap
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(typedObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存管理器：默认缓存 30 分钟，所有键按 String 序列化。
     * <p>
     * <b>关于 value 序列化器：</b>必须使用带类型信息的 ObjectMapper。
     * {@code GenericJackson2JsonRedisSerializer} 依赖 JSON 里的 {@code @class}
     * 字段还原具体类型。若 ObjectMapper 关闭了 DefaultTyping：
     * <ul>
     *   <li>写入时不含 {@code @class}；</li>
     *   <li>读取时无法还原类型，只能得到 {@code LinkedHashMap}；</li>
     *   <li>Spring 把它当作方法返回类型（如 {@code Result}）返回 →
     *       {@code ClassCastException} → 接口 500。</li>
     * </ul>
     * 复现现象：同一接口第 1 次 200（未命中缓存，走 DB），
     * 第 2 次起 500（命中缓存，反序列化成了 Map）。
     * <p>
     * 为什么不用裸 {@code activateDefaultTyping(NON_FINAL)}：那会允许任意类
     * 被实例化，存在反序列化攻击面。这里改用
     * {@link PolymorphicTypeValidator} 白名单，只允许本项目的类型，
     * 兼顾「能还原类型」与「不可被利用」。
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(typedObjectMapper())))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                // 针对特定缓存设置不同 TTL
                .withCacheConfiguration("projects", config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("users", config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("knowledge", config.entryTtl(Duration.ofHours(2)))
                .build();
    }

    /**
     * 带类型信息但仍安全的 ObjectMapper。
     * <p>
     * 与 {@link #safeObjectMapper()} 的区别：这里必须写入 {@code @class}，
     * 否则缓存读回会退化成 LinkedHashMap（详见 cacheManager 的注释）。
     * 安全性通过白名单校验器保证：只允许本项目的包与常见 JDK 容器类型，
     * 其它一律拒绝，从而避免任意的 gadget 类被实例化。
     */
    private ObjectMapper typedObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 只信任本项目与 JDK 容器类型；拒绝其它一切
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.example.mpdemo.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();

        om.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return om;
    }

    /**
     * ⚠️ 危险：<b>不要</b>把它用于任何 Redis 值的序列化。
     * <p>
     * 它关闭了 DefaultTyping，因此写出的 JSON 不含 {@code @class}，读回时一切
     * 复杂对象都会退化成 {@code LinkedHashMap}。而调用方的泛型强转
     * （如 {@code (List<KnowledgeEntry>) cached}）在运行时会擦除类型、
     * 不报错，直到该元素被真正当作目标类型使用才抛 {@code ClassCastException}——
     * 排查成本极高。AIChatService 的"系统繁忙"就是这样产生的。
     * <p>
     * 保留此方法仅为记录历史与警告；Redis 值序列化一律使用
     * {@link #typedObjectMapper()}（白名单 DefaultTyping，安全且能还原类型）。
     *
     * @deprecated Redis 值序列化请使用 {@link #typedObjectMapper()}
     */
    @Deprecated
    @SuppressWarnings("unused")
    private ObjectMapper safeObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 移除: om.activateDefaultTyping(...) - 安全风险
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }
}
