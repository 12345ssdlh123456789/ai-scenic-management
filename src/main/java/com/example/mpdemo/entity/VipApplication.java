package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_vip_application")
public class VipApplication {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String username;
    private String applyReason;
    private String status;
    private Integer adminId;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
