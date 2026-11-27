package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyVipDTO {
    @NotBlank(message = "申请理由不能为空")
    @Size(max = 500)
    private String applyReason;
}
