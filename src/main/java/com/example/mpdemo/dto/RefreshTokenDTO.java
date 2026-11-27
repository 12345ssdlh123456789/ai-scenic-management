package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token 刷新请求 DTO
 */
@Data
public class RefreshTokenDTO {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
