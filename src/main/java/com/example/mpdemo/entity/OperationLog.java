package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;

    private String username;

    /** 操作模块，如"用户管理"、"订单管理" */
    private String module;

    /** 操作动作，如"创建"、"删除"、"审核通过" */
    private String action;

    /** 操作描述 */
    private String description;

    /** 请求方法，如 GET /user/1 */
    private String requestMethod;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 响应结果 */
    private String responseResult;

    /** 操作 IP */
    private String ipAddress;

    /** 耗时（毫秒） */
    private Long duration;

    /** 操作时间 */
    private LocalDateTime operateTime;
}
