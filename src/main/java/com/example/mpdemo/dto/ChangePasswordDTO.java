package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Size(min = 6, message = "原密码长度至少6位")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度至少6位")
    private String newPassword;
}
