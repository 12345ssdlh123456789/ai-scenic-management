package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 登录响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private Integer id;
    private String username;
    private String role;
    private BigDecimal balance;
    private Integer roleLevel;
    private String token;
    private String refreshToken;
}
