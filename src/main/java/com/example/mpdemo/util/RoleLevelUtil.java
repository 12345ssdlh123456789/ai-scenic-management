package com.example.mpdemo.util;

import com.example.mpdemo.config.ScenicProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 角色等级工具类
 * 控制谁可以查看/编辑/删除谁
 * 等级数据从 ScenicProperties 读取，支持配置化
 */
@Component
public class RoleLevelUtil {

    @Autowired
    private ScenicProperties scenicProperties;

    /**
     * 获取角色等级（从配置读取，带默认值）
     */
    public int getRoleLevel(String role) {
        if (role == null) {
            return 0;
        }
        // 去除 Spring Security 的 ROLE_ 前缀
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        Integer level = scenicProperties.getRoleLevels().get(cleanRole);
        return level != null ? level : 0;
    }

    /**
     * 静态方法用于 MapStruct 映射
     */
    public static int getRoleLevelStatic(String role) {
        if (role == null) {
            return 0;
        }
        // 去除 Spring Security 的 ROLE_ 前缀
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        // 默认角色等级映射
        return switch (cleanRole) {
            case "SUPER_ADMIN" -> 4;
            case "ADMIN" -> 3;
            case "VIP" -> 2;
            case "USER" -> 1;
            default -> 0;
        };
    }

    /**
     * 判断当前角色是否可以查看目标角色信息
     * 等级高的可以查看等级低的
     */
    public boolean canView(String currentUserRole, String targetUserRole) {
        int currentLevel = getRoleLevel(currentUserRole);
        int targetLevel = getRoleLevel(targetUserRole);
        // 任一侧角色未知（等级 <= 0）时拒绝，避免 "0 >= 0" 把未知角色放行
        if (currentLevel <= 0 || targetLevel <= 0) {
            return false;
        }
        return currentLevel >= targetLevel;
    }

    /**
     * 判断当前角色是否为合法角色（在配置的等级表中）
     */
    public boolean isValidRole(String role) {
        return getRoleLevel(role) > 0;
    }

    /**
     * 判断当前角色能否管理（授予/修改为）目标角色。
     * <p>
     * 规则：调用者必须是管理员（等级 &gt;= ADMIN），且自身等级不低于目标角色，
     * 双方都必须是已知角色。
     * <p>
     * 双重约束的意义：
     * <ul>
     *   <li>{@code currentLevel >= ADMIN} —— 普通用户不能管理任何角色，
     *       即使当前接口将来漏加 {@code @PreAuthorize} 也不会被越权使用；</li>
     *   <li>{@code currentLevel >= targetLevel} —— ADMIN 不能把自己或他人
     *       提升为 SUPER_ADMIN。</li>
     * </ul>
     */
    public boolean canManageRole(String currentUserRole, String targetUserRole) {
        int currentLevel = getRoleLevel(currentUserRole);
        int targetLevel = getRoleLevel(targetUserRole);
        if (currentLevel <= 0 || targetLevel <= 0) {
            return false;
        }
        return currentLevel >= getRoleLevel("ADMIN") && currentLevel >= targetLevel;
    }

    /**
     * 判断是否为管理员（ADMIN 及以上）
     */
    public boolean isAdmin(String role) {
        return getRoleLevel(role) >= getRoleLevel("ADMIN");
    }

    /**
     * 判断是否为超级管理员
     */
    public boolean isSuperAdmin(String role) {
        return getRoleLevel(role) >= getRoleLevel("SUPER_ADMIN");
    }
}
