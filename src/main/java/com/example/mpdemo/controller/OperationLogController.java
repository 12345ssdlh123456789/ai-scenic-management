package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mpdemo.entity.OperationLog;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志管理接口
 */
@RestController
@RequestMapping("/v1/log")
@Tag(name = "操作日志", description = "系统操作审计日志查询")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "分页查询操作日志（管理员）")
    public Result getPage(@RequestParam(defaultValue = "1") Integer current,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String username,
                          @RequestParam(required = false) String module) {
        IPage<OperationLog> result = operationLogService.getPage(current, size, username, module);
        return Result.success("获取成功", result);
    }
}