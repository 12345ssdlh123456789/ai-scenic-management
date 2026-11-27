package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款申请
 * <p>
 * 退款不再由用户直接触发，而是提交申请后经管理员审批。
 * 审批通过时才退回余额并恢复库存。
 */
@Data
@TableName("t_refund_application")
public class RefundApplication {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer orderId;
    private Integer userId;
    private String username;
    private String projectName;
    private Integer quantity;
    private BigDecimal refundAmount;
    private String applyReason;
    private String status;
    private Integer adminId;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
