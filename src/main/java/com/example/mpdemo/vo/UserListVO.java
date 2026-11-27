package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户列表 VO
 * 管理员查看用户列表时使用，包含余额字段（管理员内部可见）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListVO {

    private Integer id;
    private String username;
    private String role;
    private String phone;
    private String email;
    private Integer status;
    private Integer roleLevel;
    private BigDecimal balance;
    private LocalDateTime createTime;
}
