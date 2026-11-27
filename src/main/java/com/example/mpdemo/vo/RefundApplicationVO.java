package com.example.mpdemo.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundApplicationVO {
    private Integer id;
    private Integer orderId;
    private Integer userId;
    private String username;
    private String projectName;
    private Integer quantity;
    private BigDecimal refundAmount;
    private String applyReason;
    private String status;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
