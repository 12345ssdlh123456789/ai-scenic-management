package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * VIP 申请 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipApplicationVO {

    private Integer id;
    private Integer userId;
    private String username;
    private String applyReason;
    private String status;
    private String adminRemark;
    private LocalDateTime createTime;
}
