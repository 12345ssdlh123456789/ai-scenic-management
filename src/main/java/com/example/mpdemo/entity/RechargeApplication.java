package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_recharge_application")
public class RechargeApplication {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String username;
    private BigDecimal amount;
    private String status;
    private Integer adminId;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
