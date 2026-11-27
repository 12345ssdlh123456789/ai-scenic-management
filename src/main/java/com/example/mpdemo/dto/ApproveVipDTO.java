package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApproveVipDTO {
    @NotNull(message = "申请ID不能为空")
    private Integer applicationId;

    @NotBlank(message = "操作类型不能为空")
    private String action;

    private String adminRemark;
}
