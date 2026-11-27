package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private String role;
    private String phone;
    private String email;
    private Integer status;
    private BigDecimal balance;
    private LocalDateTime vipExpireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer version;

    @TableField(exist = false)
    private List<Order> orders;
}
