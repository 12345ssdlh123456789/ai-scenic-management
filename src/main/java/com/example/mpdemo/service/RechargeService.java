package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.RechargeApplication;
import com.example.mpdemo.mapper.RechargeApplicationMapper;
import com.example.mpdemo.mapper.UserMapper;
import com.example.mpdemo.vo.RechargeApplicationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class RechargeService {

    @Autowired
    private RechargeApplicationMapper rechargeApplicationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.example.mpdemo.config.ScenicProperties scenicProperties;

    @Transactional
    public String applyRecharge(Integer userId, String username, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }
        if (scenicProperties.getRechargeMinAmount() != null &&
                amount.compareTo(scenicProperties.getRechargeMinAmount()) < 0) {
            throw new BusinessException("充值金额不能低于 " + scenicProperties.getRechargeMinAmount() + " 元");
        }
        if (scenicProperties.getRechargeMaxAmount() != null &&
                amount.compareTo(scenicProperties.getRechargeMaxAmount()) > 0) {
            throw new BusinessException("充值金额不能高于 " + scenicProperties.getRechargeMaxAmount() + " 元");
        }
        // 统一保留两位小数，避免数据库 DECIMAL(10,2) 静默四舍五入导致金额不一致
        amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);

        // 检查是否有待审核的申请，防止重复提交
        QueryWrapper<RechargeApplication> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", "PENDING");
        Long pendingCount = rechargeApplicationMapper.selectCount(queryWrapper);
        if (pendingCount > 0) {
            throw new BusinessException("您已有待审核的充值申请");
        }

        RechargeApplication application = new RechargeApplication();
        application.setUserId(userId);
        application.setUsername(username);
        application.setAmount(amount);
        application.setStatus("PENDING");
        application.setCreateTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());

        int rows = rechargeApplicationMapper.insert(application);
        if (rows > 0) {
            return "申请提交成功";
        }
        throw new BusinessException("申请提交失败");
    }

    public IPage<RechargeApplication> getPendingApplications(int current, int size, String username) {
        Page<RechargeApplication> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<RechargeApplication> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "PENDING");

        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }

        queryWrapper.orderByDesc("create_time");
        return rechargeApplicationMapper.selectPage(page, queryWrapper);
    }

    /**
     * 审批通过充值申请
     * 使用原子状态更新 + 原子余额累加，防止并发重复审批导致重复到账
     */
    @Transactional
    public String approveRecharge(Integer applicationId, Integer adminId, String remark) {
        RechargeApplication application = rechargeApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException("申请状态不正确");
        }

        // 原子更新状态：只有 PENDING 才能流转，防止并发重复审批
        int rows = rechargeApplicationMapper.updateApplicationStatus(applicationId, "APPROVED", "PENDING");
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新后再试");
        }

        // 单独更新审核人字段，避免覆盖刚更新的状态
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RechargeApplication> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", applicationId);
        wrapper.set("admin_id", adminId);
        wrapper.set("admin_remark", remark);
        rechargeApplicationMapper.update(null, wrapper);

        // 原子累加余额（单条 UPDATE，并发安全）
        int updated = userMapper.addBalance(application.getUserId(), application.getAmount());
        if (updated == 0) {
            log.warn("充值到账失败，用户ID: {}", application.getUserId());
            throw new BusinessException("充值到账失败，请稍后重试");
        }

        return "审批通过，充值成功";
    }

    @Transactional
    public String rejectRecharge(Integer applicationId, Integer adminId, String remark) {
        RechargeApplication application = rechargeApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException("申请状态不正确");
        }

        // 原子更新状态：与审批互斥，防止并发下"已通过却被标记为拒绝"导致账实不符
        int rows = rechargeApplicationMapper.updateApplicationStatus(applicationId, "REJECTED", "PENDING");
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新后再试");
        }

        // 单独更新审核人字段
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RechargeApplication> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", applicationId);
        wrapper.set("admin_id", adminId);
        wrapper.set("admin_remark", remark);
        rechargeApplicationMapper.update(null, wrapper);

        return "已拒绝该申请";
    }

    public IPage<RechargeApplication> getUserApplications(Integer userId, int current, int size) {
        Page<RechargeApplication> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<RechargeApplication> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        return rechargeApplicationMapper.selectPage(page, queryWrapper);
    }

    public IPage<RechargeApplicationVO> getUserApplicationsVO(Integer userId, int current, int size) {
        IPage<RechargeApplication> page = getUserApplications(userId, current, size);
        return page.convert(this::toVO);
    }

    public IPage<RechargeApplicationVO> getPendingApplicationsVO(int current, int size, String username) {
        IPage<RechargeApplication> page = getPendingApplications(current, size, username);
        return page.convert(this::toVO);
    }

    private RechargeApplicationVO toVO(RechargeApplication app) {
        return RechargeApplicationVO.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .username(app.getUsername())
                .amount(app.getAmount())
                .status(app.getStatus())
                .adminRemark(app.getAdminRemark())
                .createTime(app.getCreateTime())
                .updateTime(app.getUpdateTime())
                .build();
    }
}
