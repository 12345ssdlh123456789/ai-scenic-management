package com.example.mpdemo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mpdemo.entity.Order;
import com.example.mpdemo.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression guard for the global logic-delete misconfiguration.
 * <p>
 * Previously MyBatis-Plus appended {@code WHERE status='1'} to every order query
 * because application.yml configured a global {@code logic-delete-field: status}.
 * Since t_order.status holds business states ('PAID'/'CANCELLED'/...), every
 * order became invisible. This test fails loudly if that config returns.
 */
@SpringBootTest
class OrderVisibilityTest {

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void ordersAreVisibleToMyBatisPlusQueries() {
        List<Order> orders = orderMapper.selectList(new QueryWrapper<>());

        // The seeded database contains orders; a logic-delete filter would hide them all.
        assertFalse(orders.isEmpty(),
                "No orders visible. If t_order has rows, a global logic-delete-field "
                        + "is probably filtering them out with WHERE status='1'.");

        // Every returned order must carry its real business status, not a delete flag.
        for (Order order : orders) {
            assertNotNull(order.getStatus());
            assertFalse("1".equals(order.getStatus()),
                    "Order status should be a business state like PAID, not the logic-delete value '1'.");
        }
    }

    @Test
    void orderCanBeLoadedById() {
        List<Order> orders = orderMapper.selectList(new QueryWrapper<>());
        if (orders.isEmpty()) {
            return;
        }
        Integer id = orders.get(0).getId();
        assertNotNull(orderMapper.selectById(id),
                "selectById returned null for an order that exists - logic-delete filter is active.");
    }
}
