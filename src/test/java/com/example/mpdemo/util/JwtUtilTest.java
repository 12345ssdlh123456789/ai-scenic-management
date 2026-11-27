package com.example.mpdemo.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 唯一性测试。
 * <p>
 * 背景：JWT 的 iat/exp 只有秒级精度。若不带 jti，同一用户在同一秒内连续签发
 * 两次会得到完全相同的字符串，这会同时破坏两件事：
 * 1. refresh token 轮换——旧 token 作废后立刻被同值的新 token 登记回来，重放成功；
 * 2. 登出黑名单——同值的两个会话无法区分，"登出"会误杀另一个会话。
 */
class JwtUtilTest {

    /** 测试专用假密钥，与生产 .env 里的 JWT_SECRET 无关。 */
    private static final String TEST_SECRET =
            "test-only-secret-not-used-anywhere-in-production-0123456789abcdef";

    private JwtUtil newUtil() throws Exception {
        JwtUtil util = new JwtUtil(
                TEST_SECRET,
                86400000L,
                604800000L);
        return util;
    }

    @Test
    void accessTokens_issuedInSameSecond_areUnique() throws Exception {
        JwtUtil util = newUtil();
        String t1 = util.generateToken(1, "user", "USER");
        String t2 = util.generateToken(1, "user", "USER");
        assertNotEquals(t1, t2, "同一秒内签发的 access token 必须互不相同（需要唯一 jti）");
    }

    @Test
    void refreshTokens_issuedInSameSecond_areUnique() throws Exception {
        JwtUtil util = newUtil();
        String r1 = util.generateRefreshToken(1, "user", "USER");
        String r2 = util.generateRefreshToken(1, "user", "USER");
        assertNotEquals(r1, r2,
                "同一秒内签发的 refresh token 必须互不相同，否则轮换失效、重放可行");
    }

    @Test
    void refreshToken_isStillValidAndTyped() throws Exception {
        JwtUtil util = newUtil();
        String r = util.generateRefreshToken(7, "someone", "VIP");
        assertTrue(util.validateRefreshToken(r), "refresh token 应通过校验");
        assertEquals(7, util.getUserId(r));
        assertEquals("someone", util.getUsername(r));
        assertEquals("VIP", util.getRole(r));
    }

    @Test
    void accessToken_isNotAcceptedAsRefreshToken() throws Exception {
        JwtUtil util = newUtil();
        String access = util.generateToken(1, "user", "USER");
        assertFalse(util.validateRefreshToken(access),
                "access token 不应被当作 refresh token 接受");
    }

    @Test
    void shortSecret_isRejectedAtConstruction() {
        // JwtUtil 主动校验并抛出 IllegalStateException（带中文提示），
        // 使配置错误在启动阶段就暴露，而不是等到签发 token 时才失败。
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("tooShort", 1000L, 2000L),
                "长度不足 32 的密钥应在构造时被拒绝");
        assertTrue(ex.getMessage().contains("JWT_SECRET"),
                "异常信息应指明是 JWT_SECRET 配置问题");
    }

    @Test
    void blankSecret_isRejectedAtConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("   ", 1000L, 2000L));
        assertThrows(IllegalStateException.class, () -> new JwtUtil(null, 1000L, 2000L));
    }
}
