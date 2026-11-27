package com.example.mpdemo.util;

import com.example.mpdemo.config.ScenicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RoleLevelUtilTest {

    private RoleLevelUtil util;

    @BeforeEach
    void setUp() {
        ScenicProperties props = new ScenicProperties();
        props.initDefaults();
        util = new RoleLevelUtil();
        ReflectionTestUtils.setField(util, "scenicProperties", props);
    }

    @Test
    void getRoleLevel_correctValues() {
        assertEquals(1, util.getRoleLevel("USER"));
        assertEquals(2, util.getRoleLevel("VIP"));
        assertEquals(3, util.getRoleLevel("ADMIN"));
        assertEquals(4, util.getRoleLevel("SUPER_ADMIN"));
        assertEquals(0, util.getRoleLevel(null));
        assertEquals(0, util.getRoleLevel("UNKNOWN"));
    }

    @Test
    void canView_sameRole_allowed() {
        assertTrue(util.canView("USER", "USER"));
        assertTrue(util.canView("ADMIN", "ADMIN"));
    }

    @Test
    void canView_higherOverLower_allowed() {
        assertTrue(util.canView("SUPER_ADMIN", "USER"));
        assertTrue(util.canView("SUPER_ADMIN", "VIP"));
        assertTrue(util.canView("SUPER_ADMIN", "ADMIN"));
        assertTrue(util.canView("ADMIN", "USER"));
        assertTrue(util.canView("ADMIN", "VIP"));
        assertTrue(util.canView("VIP", "USER"));
    }

    @Test
    void canView_lowerOverHigher_denied() {
        assertFalse(util.canView("USER", "VIP"));
        assertFalse(util.canView("USER", "ADMIN"));
        assertFalse(util.canView("VIP", "ADMIN"));
    }

    @Test
    void canView_unknownRole_failsClosed() {
        // 修复前：双方都是未知角色时 0 >= 0 -> true（静默放行）
        assertFalse(util.canView("UNKNOWN", "UNKNOWN"),
                "未知角色之间不应互相可见");
        assertFalse(util.canView("UNKNOWN", "USER"),
                "未知角色不应能查看任何用户");
        assertFalse(util.canView("ADMIN", "UNKNOWN"),
                "不应能查看未知角色的用户");
        assertFalse(util.canView(null, null), "null 角色不应放行");
    }

    @Test
    void isValidRole_rejectsUnknownAndNull() {
        assertTrue(util.isValidRole("USER"));
        assertTrue(util.isValidRole("VIP"));
        assertTrue(util.isValidRole("ADMIN"));
        assertTrue(util.isValidRole("SUPER_ADMIN"));
        assertFalse(util.isValidRole("HACKER"));
        assertFalse(util.isValidRole(""));
        assertFalse(util.isValidRole(null));
    }

    @Test
    void canManageRole_adminCannotEscalateToSuperAdmin() {
        // 核心提权防护：ADMIN 不能授予 SUPER_ADMIN
        assertFalse(util.canManageRole("ADMIN", "SUPER_ADMIN"),
                "ADMIN 不应能把自己或他人提升为 SUPER_ADMIN");
        // ADMIN 可以管理同级及以下
        assertTrue(util.canManageRole("ADMIN", "ADMIN"));
        assertTrue(util.canManageRole("ADMIN", "VIP"));
        assertTrue(util.canManageRole("ADMIN", "USER"));
        // SUPER_ADMIN 可以管理所有
        assertTrue(util.canManageRole("SUPER_ADMIN", "SUPER_ADMIN"));
        assertTrue(util.canManageRole("SUPER_ADMIN", "ADMIN"));
        // 普通用户不能管理任何人
        assertFalse(util.canManageRole("USER", "USER"));
        // 未知角色一律拒绝
        assertFalse(util.canManageRole("UNKNOWN", "USER"));
        assertFalse(util.canManageRole("ADMIN", "UNKNOWN"));
    }

    @Test
    void canView_withRolePrefix() {
        assertTrue(util.canView("ROLE_ADMIN", "USER"));
        assertFalse(util.canView("ROLE_USER", "ADMIN"));
    }

    @Test
    void isAdmin() {
        assertFalse(util.isAdmin("USER"));
        assertFalse(util.isAdmin("VIP"));
        assertTrue(util.isAdmin("ADMIN"));
        assertTrue(util.isAdmin("SUPER_ADMIN"));
    }

    @Test
    void isSuperAdmin() {
        assertFalse(util.isSuperAdmin("ADMIN"));
        assertTrue(util.isSuperAdmin("SUPER_ADMIN"));
    }
}