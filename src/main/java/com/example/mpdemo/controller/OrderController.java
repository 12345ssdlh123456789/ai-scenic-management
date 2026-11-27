package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mpdemo.config.IdempotencyService;
import com.example.mpdemo.dto.PurchaseTicketDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.OperationLogService;
import com.example.mpdemo.service.OrderService;
import com.example.mpdemo.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/order")
@Tag(name = "订单管理", description = "门票购买、订单查询接口")
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private IdempotencyService idempotencyService;

    @PostMapping("/purchase")
    @Operation(summary = "购买门票")
    public Result purchaseTicket(@Valid @RequestBody PurchaseTicketDTO dto, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        // 幂等保护：防止双击重复购票
        String idempotencyKey = "purchase:" + details.getUserId() + ":" + dto.getProjectId() + ":" + dto.getQuantity();
        if (!idempotencyService.tryAcquire(idempotencyKey, 30)) {
            return Result.error("请勿重复提交购票请求");
        }

        try {
            String message = orderService.purchaseTicket(
                    details.getUserId(),
                    dto.getProjectId(),
                    dto.getQuantity(),
                    details.getRole()
            );

            logOperation(details, operationLogService, request, "订单管理", "购票",
                    "购买项目: " + dto.getProjectId() + " x" + dto.getQuantity(),
                    "POST /v1/order/purchase");

            return Result.success(message, null);
        } catch (Exception e) {
            // 业务失败时立即释放幂等 key，允许用户立即重试（避免 30s 内无法再提交）
            idempotencyService.release(idempotencyKey);
            throw e;
        }
    }

    @GetMapping("/myOrders")
    @Operation(summary = "查询我的订单")
    public Result getMyOrders(@RequestParam(defaultValue = "1") Integer current,
                              @RequestParam(defaultValue = "10") Integer size,
                              @RequestParam(required = false) String projectName) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        IPage<OrderVO> result = orderService.getUserOrdersVO(details.getUserId(), current, size, projectName);
        return Result.success("获取成功", result);
    }

    @GetMapping("/allOrders")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "查询所有订单（管理员）")
    public Result getAllOrders(@RequestParam(defaultValue = "1") Integer current,
                               @RequestParam(defaultValue = "10") Integer size,
                               @RequestParam(required = false) String projectName,
                               @RequestParam(required = false) String username) {
        IPage<OrderVO> result = orderService.getAllOrdersVO(current, size, projectName, username);
        return Result.success("获取成功", result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询订单详情")
    public Result getOrder(@PathVariable Integer id) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        OrderVO order = orderService.getOrderByIdVO(id, details.getRole(), details.getUserId());
        if (order == null) {
            return Result.error("订单不存在或权限不足");
        }

        return Result.success("获取成功", order);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取消订单")
    public Result cancelOrder(@PathVariable Integer id, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String message = orderService.cancelOrder(id, details.getUserId());

        logOperation(details, operationLogService, request, "订单管理", "取消订单",
                "取消订单ID: " + id, "PUT /v1/order/" + id + "/cancel");

        return Result.success(message, null);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "申请退款（需管理员审批）")
    public Result applyRefund(@PathVariable Integer id,
                              @RequestBody(required = false) com.example.mpdemo.dto.ApplyRefundDTO dto,
                              HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String reason = dto != null ? dto.getApplyReason() : null;
        String message = orderService.applyRefund(id, details.getUserId(), reason);

        logOperation(details, operationLogService, request, "订单管理", "申请退款",
                "申请退款订单ID: " + id, "POST /v1/order/" + id + "/refund");

        return Result.success(message, null);
    }

    @GetMapping("/myRefunds")
    @Operation(summary = "查询我的退款申请")
    public Result getMyRefunds(@RequestParam(defaultValue = "1") Integer current,
                               @RequestParam(defaultValue = "10") Integer size) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }
        return Result.success("获取成功",
                orderService.getUserRefundApplications(details.getUserId(), current, size));
    }

    @GetMapping("/pendingRefunds")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "查询待审批退款申请（管理员）")
    public Result getPendingRefunds(@RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size,
                                    @RequestParam(required = false) String username) {
        return Result.success("获取成功",
                orderService.getPendingRefundApplications(current, size, username));
    }

    @PostMapping("/approveRefund")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "审批退款申请（管理员）")
    public Result approveRefund(@Valid @RequestBody com.example.mpdemo.dto.ApproveRefundDTO dto,
                                HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        String message = orderService.approveRefund(
                dto.getApplicationId(), dto.getAction(), dto.getAdminRemark(), details.getUserId());

        logOperation(details, operationLogService, request, "订单管理", "退款审批",
                "审批退款申请ID: " + dto.getApplicationId() + " 结果: " + dto.getAction(),
                "POST /v1/order/approveRefund");

        return Result.success(message, null);
    }
}