package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.mpdemo.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private Integer projectId;
    private String projectName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private LocalDateTime orderTime;
    private String status;
    @Version
    private Integer version;

    @TableField(exist = false)
    private String username;
}
