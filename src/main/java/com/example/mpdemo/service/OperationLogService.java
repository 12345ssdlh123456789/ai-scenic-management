package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.entity.OperationLog;
import com.example.mpdemo.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志服务
 * 异步写入，不阻塞主业务流程
 */
@Slf4j
@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 异步记录操作日志
     * 使用 asyncExecutor 线程池（AsyncConfig 配置）
     */
    @Async("asyncExecutor")
    public void log(Integer userId, String username, String module, String action,
                    String description, String requestMethod, String requestParams,
                    String responseResult, String ipAddress, Long duration) {
        try {
            OperationLog operationLog = new OperationLog();
            operationLog.setUserId(userId);
            operationLog.setUsername(username);
            operationLog.setModule(module);
            operationLog.setAction(action);
            operationLog.setDescription(description);
            operationLog.setRequestMethod(requestMethod);
            operationLog.setRequestParams(requestParams);
            operationLog.setResponseResult(responseResult);
            operationLog.setIpAddress(ipAddress);
            operationLog.setDuration(duration);
            operationLog.setOperateTime(LocalDateTime.now());

            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    /**
     * 分页查询操作日志（管理员）
     */
    public IPage<OperationLog> getPage(int current, int size, String username, String module) {
        Page<OperationLog> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<OperationLog> queryWrapper = new QueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }
        if (module != null && !module.trim().isEmpty()) {
            queryWrapper.like("module", module.trim());
        }
        queryWrapper.orderByDesc("operate_time");
        return operationLogMapper.selectPage(page, queryWrapper);
    }
}
