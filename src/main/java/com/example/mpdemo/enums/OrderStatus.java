package com.example.mpdemo.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 * <p>
 * 退款流程：PAID/COMPLETED --申请退款--> REFUND_PENDING --管理员审批通过--> REFUNDED
 *                                                --管理员审批拒绝--> 回到原状态
 */
@Getter
public enum OrderStatus {
    PAID("PAID", "已支付"),
    CANCELLED("CANCELLED", "已取消"),
    REFUND_PENDING("REFUND_PENDING", "退款审核中"),
    REFUNDED("REFUNDED", "已退款"),
    COMPLETED("COMPLETED", "已完成");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}