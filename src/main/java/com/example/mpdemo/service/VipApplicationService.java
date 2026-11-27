package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.entity.VipApplication;
import com.example.mpdemo.mapper.UserMapper;
import com.example.mpdemo.mapper.VipApplicationMapper;
import com.example.mpdemo.vo.VipApplicationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VipApplicationService {

    @Autowired
    private VipApplicationMapper vipApplicationMapper;

    @Autowired
    private UserMapper userMapper;

    public String submitVipApplication(Integer userId, String username, String applyReason) {
        QueryWrapper<VipApplication> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", "PENDING");
        Long pendingCount = vipApplicationMapper.selectCount(queryWrapper);

        if (pendingCount > 0) {
            throw new BusinessException("您已有待审核的VIP申请");
        }

        VipApplication application = new VipApplication();
        application.setUserId(userId);
        application.setUsername(username);
        application.setApplyReason(applyReason);
        application.setStatus("PENDING");

        int rows = vipApplicationMapper.insert(application);
        if (rows > 0) {
            return "申请提交成功，请等待管理员审核";
        }
        throw new BusinessException("申请提交失败");
    }

    public VipApplication getMyApplication(Integer userId) {
        QueryWrapper<VipApplication> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        queryWrapper.last("LIMIT 1");

        return vipApplicationMapper.selectOne(queryWrapper);
    }

    public IPage<VipApplication> getAllApplications(int current, int size, String username) {
        Page<VipApplication> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<VipApplication> queryWrapper = new QueryWrapper<>();

        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }

        queryWrapper.orderByDesc("create_time");
        return vipApplicationMapper.selectPage(page, queryWrapper);
    }

    @Transactional
    public String approveApplication(Integer applicationId, String action, String adminRemark, Integer adminId) {
        VipApplication application = vipApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException("该申请已处理");
        }

        String newStatus = "APPROVED".equals(action) ? "APPROVED" : "REJECTED";

        // 防止并发审核：原子状态更新
        int updated = vipApplicationMapper.updateApplicationStatus(applicationId, newStatus, "PENDING");
        if (updated == 0) {
            throw new BusinessException("申请已被其他管理员处理，请刷新后再试");
        }

        // 只更新 admin 审核字段，避免把上面刚更新的 status 覆盖回旧值（PENDING）
        UpdateWrapper<VipApplication> remarkWrapper = new UpdateWrapper<>();
        remarkWrapper.eq("id", applicationId);
        remarkWrapper.set("admin_id", adminId);
        remarkWrapper.set("admin_remark", adminRemark);
        vipApplicationMapper.update(null, remarkWrapper);

        if ("APPROVED".equals(action)) {
            User user = userMapper.selectById(application.getUserId());
            if (user != null) {
                // 仅在用户当前不是 VIP/ADMIN 时升级，避免覆盖现有角色
                if ("USER".equals(user.getRole())) {
                    user.setRole("VIP");
                    user.setVipExpireTime(LocalDateTime.now().plusDays(30));
                    userMapper.updateById(user);
                }
            }
        }

        return "审核完成";
    }

    public VipApplicationVO getMyApplicationVO(Integer userId) {
        VipApplication app = getMyApplication(userId);
        return app != null ? toVO(app) : null;
    }

    public IPage<VipApplicationVO> getAllApplicationsVO(int current, int size, String username) {
        IPage<VipApplication> page = getAllApplications(current, size, username);
        return page.convert(this::toVO);
    }

    private VipApplicationVO toVO(VipApplication app) {
        return VipApplicationVO.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .username(app.getUsername())
                .applyReason(app.getApplyReason())
                .status(app.getStatus())
                .adminRemark(app.getAdminRemark())
                .createTime(app.getCreateTime())
                .build();
    }
}
