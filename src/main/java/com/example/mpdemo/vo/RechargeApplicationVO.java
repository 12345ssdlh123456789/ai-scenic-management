package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值申请 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RechargeApplicationVO {

    private Integer id;
    private Integer userId;
    private String username;
    private BigDecimal amount;
    private String status;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
