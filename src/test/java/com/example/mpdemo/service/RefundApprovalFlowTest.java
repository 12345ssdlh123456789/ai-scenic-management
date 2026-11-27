package com.example.mpdemo.service;

import com.example.mpdemo.entity.Order;
import com.example.mpdemo.entity.ScenicProject;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.mapper.OrderMapper;
import com.example.mpdemo.mapper.ScenicProjectMapper;
import com.example.mpdemo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mpdemo.entity.RefundApplication;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 退款审批流测试。
 * <p>
 * 核心业务规则（用户需求）：
 * 退款必须经管理员审批；审批通过后退回余额，且门票库存 +数量。
 * 审批拒绝则不退款、不恢复库存，订单还原为可再次申请的状态。
 */
@SpringBootTest
class RefundApprovalFlowTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ScenicProjectMapper scenicProjectMapper;

    @Autowired
    private com.example.mpdemo.mapper.RefundApplicationMapper refundApplicationMapper;

    /** 造一笔 PAID 订单：直接插入，避免依赖购票路径的库存。 */
    private Order createPaidOrder(Integer userId, Integer projectId, int quantity, BigDecimal total) {
        Order order = new Order();
        order.setUserId(userId);
        order.setProjectId(projectId);
        order.setProjectName("测试项目");
        order.setQuantity(quantity);
        order.setUnitPrice(total.divide(new BigDecimal(quantity), 2, java.math.RoundingMode.HALF_UP));
        order.setTotalPrice(total);
        order.setOrderTime(java.time.LocalDateTime.now());
        order.setStatus("PAID");
        order.setVersion(0);
        orderMapper.insert(order);
        return order;
    }

    @Test
    @Transactional
    void approveRefund_returnsMoneyAndRestoresStock() {
        // 准备
        User user = userMapper.selectList(new QueryWrapper<>()).get(0);
        ScenicProject project = scenicProjectMapper.selectList(new QueryWrapper<>()).get(0);

        BigDecimal startBalance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        Integer startStock = project.getStock();
        BigDecimal refundAmount = new BigDecimal("100.00");
        int quantity = 4;

        Order order = createPaidOrder(user.getId(), project.getId(), quantity, refundAmount);

        // 用户申请退款 -> 订单进入 REFUND_PENDING
        orderService.applyRefund(order.getId(), user.getId(), "行程有变");
        assertEquals("REFUND_PENDING", orderMapper.selectById(order.getId()).getStatus(),
                "申请退款后订单应处于 REFUND_PENDING");

        // 找到该申请
        RefundApplication app = refundApplicationMapper.selectOne(
                new QueryWrapper<RefundApplication>().eq("order_id", order.getId()));
        assertNotNull(app, "应生成退款申请记录");
        assertEquals("PENDING", app.getStatus());

        // 管理员审批通过
        orderService.approveRefund(app.getId(), "APPROVED", "同意退款", user.getId());

        // 断言：余额退回
        BigDecimal endBalance = userMapper.selectById(user.getId()).getBalance();
        assertEquals(0, endBalance.compareTo(startBalance.add(refundAmount)),
                "审批通过后应退回余额 " + refundAmount);

        // 断言：库存 +quantity（用户需求：票的库存也增加）
        Integer endStock = scenicProjectMapper.selectById(project.getId()).getStock();
        assertEquals(startStock + quantity, endStock,
                "审批通过后库存应增加 " + quantity);

        // 断言：订单变为 REFUNDED
        assertEquals("REFUNDED", orderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    @Transactional
    void rejectRefund_doesNotMoveMoneyOrStock() {
        User user = userMapper.selectList(new QueryWrapper<>()).get(0);
        ScenicProject project = scenicProjectMapper.selectList(new QueryWrapper<>()).get(0);

        BigDecimal startBalance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        Integer startStock = project.getStock();

        Order order = createPaidOrder(user.getId(), project.getId(), 2, new BigDecimal("50.00"));
        orderService.applyRefund(order.getId(), user.getId(), "不想去了");

        RefundApplication app = refundApplicationMapper.selectOne(
                new QueryWrapper<RefundApplication>().eq("order_id", order.getId()));

        // 管理员拒绝
        orderService.approveRefund(app.getId(), "REJECTED", "不符合退款条件", user.getId());

        // 余额与库存均不变
        assertEquals(0, userMapper.selectById(user.getId()).getBalance().compareTo(startBalance),
                "拒绝退款不应改变余额");
        assertEquals(startStock, scenicProjectMapper.selectById(project.getId()).getStock(),
                "拒绝退款不应改变库存");

        // 订单还原为 PAID，用户可再次申请
        assertEquals("PAID", orderMapper.selectById(order.getId()).getStatus(),
                "拒绝后订单应还原为 PAID");
    }

    @Test
    @Transactional
    void duplicateApproval_isRejected() {
        User user = userMapper.selectList(new QueryWrapper<>()).get(0);
        ScenicProject project = scenicProjectMapper.selectList(new QueryWrapper<>()).get(0);

        Order order = createPaidOrder(user.getId(), project.getId(), 1, new BigDecimal("30.00"));
        orderService.applyRefund(order.getId(), user.getId(), "重复审批测试");

        RefundApplication app = refundApplicationMapper.selectOne(
                new QueryWrapper<RefundApplication>().eq("order_id", order.getId()));

        orderService.approveRefund(app.getId(), "APPROVED", "第一次", user.getId());

        // 第二次审批必须失败，防止重复退款
        assertThrows(Exception.class,
                () -> orderService.approveRefund(app.getId(), "APPROVED", "第二次", user.getId()),
                "重复审批应被拒绝（防止重复退款）");
    }
}
