package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员审批退款申请
 */
@Data
public class ApproveRefundDTO {
    @NotNull(message = "申请ID不能为空")
    private Integer applicationId;

    /** APPROVED 或 REJECTED */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    @Size(max = 500, message = "审核备注不能超过500字")
    private String adminRemark;
}
