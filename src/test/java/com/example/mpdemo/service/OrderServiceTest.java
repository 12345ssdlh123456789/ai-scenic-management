package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.config.ScenicProperties;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.Order;
import com.example.mpdemo.entity.RefundApplication;
import com.example.mpdemo.entity.ScenicProject;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.mapper.OrderMapper;
import com.example.mpdemo.mapper.RefundApplicationMapper;
import com.example.mpdemo.mapper.ScenicProjectMapper;
import com.example.mpdemo.mapper.UserMapper;
import com.example.mpdemo.util.RoleLevelUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ScenicProjectMapper scenicProjectMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefundApplicationMapper refundApplicationMapper;

    @Mock
    private ScenicProperties scenicProperties;

    @Mock
    private RoleLevelUtil roleLevelUtil;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private ScenicProject testProject;

    @BeforeEach
    void setUp() {
        when(scenicProperties.getMaxTicketQuantity()).thenReturn(10);
        when(scenicProperties.getVipDiscount()).thenReturn(new BigDecimal("0.8"));

        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setRole("USER");
        testUser.setBalance(new BigDecimal("500.00"));

        testProject = new ScenicProject();
        testProject.setId(1);
        testProject.setName("过山车");
        testProject.setPrice(new BigDecimal("80.00"));
        testProject.setStock(100);
        testProject.setStatus(1);
    }

    @Test
    void purchaseTicket_success() {
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);
        when(scenicProjectMapper.deductStock(1, 2)).thenReturn(1);
        when(userMapper.selectById(1)).thenReturn(testUser);
        when(userMapper.deductBalance(1, new BigDecimal("160.00"))).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        String result = orderService.purchaseTicket(1, 1, 2, "USER");

        assertEquals("购票成功", result);
        // 原子扣款 + 原子扣库存，不再 read-modify-write 用户余额对象
        verify(userMapper).deductBalance(1, new BigDecimal("160.00"));
        verify(scenicProjectMapper).deductStock(1, 2);
        verify(orderMapper).insert(any(Order.class));
    }

    @Test
    void purchaseTicket_stockInsufficient() {
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);
        when(scenicProjectMapper.deductStock(1, 2)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.purchaseTicket(1, 1, 2, "USER"));
        assertTrue(ex.getMessage().contains("库存不足"));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void purchase_ticket_insufficient_balance() {
        testUser.setBalance(new BigDecimal("10.00"));
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);
        when(scenicProjectMapper.deductStock(1, 2)).thenReturn(1);
        when(userMapper.selectById(1)).thenReturn(testUser);
        when(userMapper.deductBalance(1, new BigDecimal("160.00"))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.purchaseTicket(1, 1, 2, "USER"));
        assertTrue(ex.getMessage().contains("余额不足"));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void purchase_ticket_exceed_max_quantity() {
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.purchaseTicket(1, 1, 11, "USER"));
        assertTrue(ex.getMessage().contains("最多购买"));
        verify(orderMapper, never()).insert(any());
        verify(scenicProjectMapper, never()).deductStock(anyInt(), anyInt());
    }

    @Test
    void purchase_ticket_vip_discount() {
        testUser.setBalance(new BigDecimal("500.00"));
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);
        when(scenicProjectMapper.deductStock(1, 2)).thenReturn(1);
        when(userMapper.selectById(1)).thenReturn(testUser);
        when(userMapper.deductBalance(1, new BigDecimal("128.00"))).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        String result = orderService.purchaseTicket(1, 1, 2, "VIP");

        assertEquals("购票成功", result);
        // VIP 8折: 80*2*0.8 = 128
        verify(userMapper).deductBalance(1, new BigDecimal("128.00"));
    }

    @Test
    void purchase_ticket_invalid_quantity_throws() {
        assertThrows(BusinessException.class, () ->
                orderService.purchaseTicket(1, 1, -1, "USER")
        );
    }

    @Test
    void purchase_ticket_project_not_found() {
        when(scenicProjectMapper.selectById(99)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.purchaseTicket(1, 99, 1, "USER"));
        assertEquals("项目不存在或已下架", ex.getMessage());
    }

    @Test
    void purchase_ticket_project_disabled() {
        testProject.setStatus(0);
        when(scenicProjectMapper.selectById(1)).thenReturn(testProject);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.purchaseTicket(1, 1, 1, "USER"));
        assertEquals("项目不存在或已下架", ex.getMessage());
    }

    @Test
    void get_user_orders_returns_page() {
        when(orderMapper.selectPage(any(), any(QueryWrapper.class))).thenReturn(new Page<>());

        IPage<Order> result = orderService.getUserOrders(1, 1, 10, null);

        assertNotNull(result);
        verify(orderMapper).selectPage(any(), any(QueryWrapper.class));
    }

    @Test
    void get_order_by_id_admin_sees_any_order() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(99);
        when(orderMapper.selectById(1)).thenReturn(order);
        when(roleLevelUtil.isAdmin("ADMIN")).thenReturn(true);

        Order result = orderService.getOrderById(1, "ADMIN", 1);

        assertNotNull(result);
        assertEquals(99, result.getUserId());
    }

    @Test
    void get_order_by_id_regular_user_cannot_see_others() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(99);
        when(orderMapper.selectById(1)).thenReturn(order);
        when(roleLevelUtil.isAdmin("USER")).thenReturn(false);

        Order result = orderService.getOrderById(1, "USER", 1);

        assertNull(result);
    }

    @Test
    void cancel_order_restores_stock_and_balance() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(1);
        order.setProjectId(1);
        order.setQuantity(2);
        order.setTotalPrice(new BigDecimal("160.00"));
        order.setStatus("PAID");

        when(orderMapper.selectById(1)).thenReturn(order);
        when(orderMapper.updateOrderStatus(1, "CANCELLED", "PAID")).thenReturn(1);
        when(userMapper.addBalance(1, new BigDecimal("160.00"))).thenReturn(1);

        String result = orderService.cancelOrder(1, 1);

        assertEquals("取消成功", result);
        // 原子恢复库存 + 原子恢复余额
        verify(scenicProjectMapper).restoreStock(1, 2);
        verify(userMapper).addBalance(1, new BigDecimal("160.00"));
    }

    @Test
    void cancel_order_wrong_user() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(99);
        when(orderMapper.selectById(1)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1, 1));
        assertEquals("无权操作此订单", ex.getMessage());
    }

    @Test
    void cancel_order_non_paid_status() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(1);
        order.setStatus("COMPLETED");
        when(orderMapper.selectById(1)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1, 1));
        assertEquals("仅已支付的订单可以取消", ex.getMessage());
    }

    @Test
    void applyRefund_movesOrderToPending_andCreatesApplication() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(1);
        order.setProjectId(1);
        order.setQuantity(2);
        order.setTotalPrice(new BigDecimal("160.00"));
        order.setStatus("COMPLETED");

        when(orderMapper.selectById(1)).thenReturn(order);
        when(orderMapper.updateOrderStatus(1, "REFUND_PENDING", "COMPLETED")).thenReturn(1);

        String result = orderService.applyRefund(1, 1, "临时有事");

        assertTrue(result.contains("等待管理员审核"), "应返回提交成功提示");
        // 申请阶段绝不退款、绝不恢复库存
        verify(userMapper, never()).addBalance(anyInt(), any());
        verify(scenicProjectMapper, never()).restoreStock(anyInt(), anyInt());
        verify(refundApplicationMapper).insert(any());
    }

    @Test
    void applyRefund_rejectsWhenOrderNotRefundable() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(1);
        order.setStatus("CANCELLED");

        when(orderMapper.selectById(1)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.applyRefund(1, 1, "理由"));
        assertEquals("仅已支付或已完成的订单可以申请退款", ex.getMessage());
    }

    @Test
    void approveRefund_paidOrder_refundsMoneyAndRestoresStock() {
        RefundApplication app = new RefundApplication();
        app.setId(5);
        app.setOrderId(1);
        app.setUserId(1);
        app.setQuantity(2);
        app.setRefundAmount(new BigDecimal("160.00"));
        app.setStatus("PENDING");

        Order order = new Order();
        order.setId(1);
        order.setProjectId(7);
        order.setQuantity(2);
        order.setStatus("REFUND_PENDING");

        when(refundApplicationMapper.selectById(5)).thenReturn(app);
        when(refundApplicationMapper.updateApplicationStatus(5, "APPROVED", "PENDING")).thenReturn(1);
        when(orderMapper.updateOrderStatus(1, "REFUNDED", "REFUND_PENDING")).thenReturn(1);
        when(userMapper.addBalance(1, new BigDecimal("160.00"))).thenReturn(1);
        when(orderMapper.selectById(1)).thenReturn(order);

        String result = orderService.approveRefund(5, "APPROVED", "同意", 99);

        assertTrue(result.contains("已退款"), "应提示退款成功");
        // 审批通过：退钱 + 恢复库存（用户明确要求库存也要加回来）
        verify(userMapper).addBalance(1, new BigDecimal("160.00"));
        verify(scenicProjectMapper).restoreStock(7, 2);
    }

    @Test
    void approveRefund_rejected_doesNotMoveMoneyOrStock() {
        RefundApplication app = new RefundApplication();
        app.setId(6);
        app.setOrderId(1);
        app.setUserId(1);
        app.setQuantity(2);
        app.setRefundAmount(new BigDecimal("160.00"));
        app.setStatus("PENDING");

        when(refundApplicationMapper.selectById(6)).thenReturn(app);
        when(refundApplicationMapper.updateApplicationStatus(6, "REJECTED", "PENDING")).thenReturn(1);

        String result = orderService.approveRefund(6, "REJECTED", "不符合条件", 99);

        assertTrue(result.contains("拒绝"), "应提示已拒绝");
        verify(userMapper, never()).addBalance(anyInt(), any());
        verify(scenicProjectMapper, never()).restoreStock(anyInt(), anyInt());
        // 订单还原为 PAID
        verify(orderMapper).updateOrderStatus(1, "PAID", "REFUND_PENDING");
    }

    @Test
    void approveRefund_alreadyProcessed_isRejected() {
        RefundApplication app = new RefundApplication();
        app.setId(7);
        app.setStatus("APPROVED");

        when(refundApplicationMapper.selectById(7)).thenReturn(app);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.approveRefund(7, "APPROVED", "重复", 99));
        assertEquals("该申请已处理", ex.getMessage());
        verify(userMapper, never()).addBalance(anyInt(), any());
    }
}