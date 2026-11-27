package com.example.mpdemo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务配置属性
 * 通过 application.properties/yml 可配置，支持热更新（结合 @RefreshScope 使用）
 */
@Data
@Component
@ConfigurationProperties(prefix = "scenic")
public class ScenicProperties {

    /** VIP 折扣 */
    private BigDecimal vipDiscount = new BigDecimal("0.8");

    /** 角色等级映射 */
    private Map<String, Integer> roleLevels = new HashMap<>();

    /** 最大单次购票数量 */
    private int maxTicketQuantity = 10;

    /** 登录失败锁定次数 */
    private int loginMaxAttempts = 5;

    /** 登录锁定时间（分钟） */
    private long loginLockMinutes = 10;

    /** 注册限流：每分钟最大次数 */
    private int registerRateLimit = 5;

    /** 登录限流：每分钟最大次数 */
    private int loginRateLimit = 20;

    /** AI 聊天限流：每分钟最大次数 */
    private int aiChatRateLimit = 10;

    /** 订单超时时间（分钟），超时自动取消 */
    private long orderTimeoutMinutes = 30;

    /** VIP 默认有效天数 */
    private int vipValidDays = 30;

    /** 充值最小金额 */
    private BigDecimal rechargeMinAmount = new BigDecimal("1.00");

    /** 充值最大金额 */
    private BigDecimal rechargeMaxAmount = new BigDecimal("10000.00");

    /** 初始化配置 */
    @PostConstruct
    public void initDefaults() {
        if (roleLevels.isEmpty()) {
            roleLevels.put("USER", 1);
            roleLevels.put("VIP", 2);
            roleLevels.put("ADMIN", 3);
            roleLevels.put("SUPER_ADMIN", 4);
        }
    }
}
