package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mpdemo.dto.ApproveRechargeDTO;
import com.example.mpdemo.dto.RechargeApplyDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.OperationLogService;
import com.example.mpdemo.service.RechargeService;
import com.example.mpdemo.vo.RechargeApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/recharge")
@Tag(name = "充值管理", description = "充值申请、审批接口")
public class RechargeController extends BaseController {

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/apply")
    @Operation(summary = "申请充值")
    public Result applyRecharge(@Valid @RequestBody RechargeApplyDTO dto, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String message = rechargeService.applyRecharge(details.getUserId(), details.getUsername(), dto.getAmount());

        logOperation(details, operationLogService, request, "充值管理", "申请充值",
                "申请充值金额: ¥" + dto.getAmount(), "POST /v1/recharge/apply");

        return Result.success(message, null);
    }

    @GetMapping("/myApplications")
    @Operation(summary = "查看我的充值申请")
    public Result getMyApplications(@RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        IPage<RechargeApplicationVO> result = rechargeService.getUserApplicationsVO(details.getUserId(), current, size);
        return Result.success("获取成功", result);
    }

    @GetMapping("/pendingApplications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "查看待审批充值申请（管理员）")
    public Result getPendingApplications(@RequestParam(defaultValue = "1") Integer current,
                                         @RequestParam(defaultValue = "10") Integer size,
                                         @RequestParam(required = false) String username) {
        IPage<RechargeApplicationVO> result = rechargeService.getPendingApplicationsVO(current, size, username);
        return Result.success("获取成功", result);
    }

    @PostMapping("/approve/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "通过充值申请")
    public Result approveRecharge(@PathVariable Integer id,
                                  @Valid @RequestBody ApproveRechargeDTO dto,
                                  HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String message = rechargeService.approveRecharge(id, details.getUserId(), dto.getRemark() != null ? dto.getRemark() : "");

        logOperation(details, operationLogService, request, "充值管理", "审批通过",
                "通过充值申请ID: " + id, "POST /v1/recharge/approve/" + id);

        return Result.success(message, null);
    }

    @PostMapping("/reject/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "拒绝充值申请")
    public Result rejectRecharge(@PathVariable Integer id,
                                 @Valid @RequestBody ApproveRechargeDTO dto,
                                 HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String message = rechargeService.rejectRecharge(id, details.getUserId(), dto.getRemark() != null ? dto.getRemark() : "");

        logOperation(details, operationLogService, request, "充值管理", "拒绝",
                "拒绝充值申请ID: " + id, "POST /v1/recharge/reject/" + id);

        return Result.success(message, null);
    }
}