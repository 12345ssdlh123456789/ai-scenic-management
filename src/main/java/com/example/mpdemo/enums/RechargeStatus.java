package com.example.mpdemo.enums;

import lombok.Getter;

/**
 * 充值申请状态枚举
 */
@Getter
public enum RechargeStatus {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;

    RechargeStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RechargeStatus fromCode(String code) {
        for (RechargeStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}