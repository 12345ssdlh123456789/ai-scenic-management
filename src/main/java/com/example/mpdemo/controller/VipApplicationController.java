package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mpdemo.dto.ApplyVipDTO;
import com.example.mpdemo.dto.ApproveVipDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.OperationLogService;
import com.example.mpdemo.service.VipApplicationService;
import com.example.mpdemo.vo.VipApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/vip")
@Tag(name = "VIP管理", description = "VIP申请、审核接口")
public class VipApplicationController extends BaseController {

    @Autowired
    private VipApplicationService vipApplicationService;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/apply")
    @Operation(summary = "申请VIP")
    public Result applyForVip(@Valid @RequestBody ApplyVipDTO dto, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        if (!"USER".equals(details.getRole())) {
            return Result.error("只有普通用户可以申请VIP");
        }

        String message = vipApplicationService.submitVipApplication(
                details.getUserId(),
                details.getUsername(),
                dto.getApplyReason()
        );

        logOperation(details, operationLogService, request, "VIP管理", "申请VIP",
                "提交VIP申请", "POST /v1/vip/apply");

        return Result.success(message, null);
    }

    @GetMapping("/myApplication")
    @Operation(summary = "查看我的VIP申请")
    public Result getMyApplication() {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        VipApplicationVO application = vipApplicationService.getMyApplicationVO(details.getUserId());
        return Result.success("获取成功", application);
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "查看所有VIP申请（管理员）")
    public Result getAllApplications(@RequestParam(defaultValue = "1") Integer current,
                                     @RequestParam(defaultValue = "10") Integer size,
                                     @RequestParam(required = false) String username) {
        IPage<VipApplicationVO> result = vipApplicationService.getAllApplicationsVO(current, size, username);
        return Result.success("获取成功", result);
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "审核VIP申请")
    public Result approveApplication(@Valid @RequestBody ApproveVipDTO dto, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        if (!"APPROVED".equals(dto.getAction()) && !"REJECTED".equals(dto.getAction())) {
            return Result.error("操作类型错误");
        }

        String message = vipApplicationService.approveApplication(
                dto.getApplicationId(),
                dto.getAction(),
                dto.getAdminRemark(),
                details.getUserId()
        );

        String action = "APPROVED".equals(dto.getAction()) ? "通过VIP" : "拒绝VIP";
        logOperation(details, operationLogService, request, "VIP管理", action,
                "审核VIP申请ID: " + dto.getApplicationId(), "POST /v1/vip/approve");

        return Result.success(message, null);
    }
}