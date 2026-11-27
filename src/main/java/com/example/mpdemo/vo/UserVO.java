package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息 VO（不包含密码等敏感字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Integer id;
    private String username;
    private String role;
    private String phone;
    private String email;
    private Integer status;
    private BigDecimal balance;
    private Integer roleLevel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
