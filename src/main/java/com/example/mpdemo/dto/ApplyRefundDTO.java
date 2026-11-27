package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交退款申请
 */
@Data
public class ApplyRefundDTO {
    @NotNull(message = "订单ID不能为空")
    private Integer orderId;

    @Size(max = 500, message = "退款理由不能超过500字")
    private String applyReason;
}
