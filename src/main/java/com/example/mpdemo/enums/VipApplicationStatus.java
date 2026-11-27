package com.example.mpdemo.enums;

import lombok.Getter;

/**
 * VIP 申请状态枚举
 */
@Getter
public enum VipApplicationStatus {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;

    VipApplicationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static VipApplicationStatus fromCode(String code) {
        for (VipApplicationStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}