package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.config.ScenicProperties;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.Order;
import com.example.mpdemo.entity.RefundApplication;
import com.example.mpdemo.entity.ScenicProject;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.mapper.OrderMapper;
import com.example.mpdemo.mapper.ScenicProjectMapper;
import com.example.mpdemo.mapper.UserMapper;
import com.example.mpdemo.util.RoleLevelUtil;
import com.example.mpdemo.vo.OrderVO;
import com.example.mpdemo.vo.RefundApplicationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private ScenicProperties scenicProperties;

    /**
     * 获取当前配置的 VIP 折扣
     */
    private BigDecimal getVipDiscount() {
        return scenicProperties.getVipDiscount();
    }

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ScenicProjectMapper scenicProjectMapper;

    @Autowired
    private RoleLevelUtil roleLevelUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.example.mpdemo.mapper.RefundApplicationMapper refundApplicationMapper;

    @Autowired(required = false)
    private CacheManager cacheManager;

    /**
     * 清除项目相关缓存（购票/取消/退款都会修改项目库存）。
     * <p>
     * 使用 allEntries=true 而非硬编码单个 key：读路径的 key 由
     * {@code ScenicProjectController.listProjects} 用 current/size/name 动态拼成
     * （如 {@code list:1:10:过山车}），硬编码 {@code list:1:10:} 只能命中"无筛选首页"
     * 这一种组合，其余分页/搜索缓存以及 {@code detail:<id>} 都不会失效，
     * 导致库存与价格长时间陈旧。
     * <p>
     * 购票/取消/退款不是高频操作，整体清空 projects 缓存的代价完全可以接受。
     */
    private void evictProjectCache() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("projects");
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * 购买门票（事务保证余额与库存一致性）
     * 使用 @Transactional 确保：扣库存、扣余额、建订单 三步要么全部成功，要么全部回滚。
     * 购票失败时自动恢复用户余额（之前已扣除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String purchaseTicket(Integer userId, Integer projectId, Integer quantity, String userRole) {
        if (quantity <= 0) {
            throw new BusinessException("购票数量必须大于0");
        }
        if (quantity > scenicProperties.getMaxTicketQuantity()) {
            throw new BusinessException("单次最多购买 " + scenicProperties.getMaxTicketQuantity() + " 张门票");
        }

        // 1. 检查项目是否存在且已上架
        ScenicProject project = scenicProjectMapper.selectById(projectId);
        if (project == null || project.getStatus() == null || project.getStatus() != 1) {
            throw new BusinessException("项目不存在或已下架");
        }

        BigDecimal unitPrice = project.getPrice();
        BigDecimal totalPrice = unitPrice.multiply(new BigDecimal(quantity));
        // VIP 及管理员（ADMIN/SUPER_ADMIN）均享 8 折优惠
        if ("VIP".equals(userRole) || "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            totalPrice = totalPrice.multiply(getVipDiscount());
        }
        // 统一保留两位小数，避免单价*数量与总价出现分位差异
        totalPrice = totalPrice.setScale(2, java.math.RoundingMode.HALF_UP);

        // 2. 原子扣减库存（并发安全）
        int deducted = scenicProjectMapper.deductStock(projectId, quantity);
        if (deducted == 0) {
            throw new BusinessException("库存不足或项目已下架");
        }

        // 3. 校验用户存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 4. 原子扣减余额（含余额检查）。
        //    单条 UPDATE 保证并发下不会超扣（balance >= totalPrice 才扣）。
        //    余额不足时抛异常，事务回滚会自动恢复已扣库存。
        int balanceUpdated = userMapper.deductBalance(userId, totalPrice);
        if (balanceUpdated == 0) {
            throw new BusinessException("余额不足");
        }

        // 5. 创建订单
        Order order = new Order();
        order.setUserId(userId);
        order.setProjectId(projectId);
        order.setProjectName(project.getName());
        order.setQuantity(quantity);
        // 单价 = 折后总价 / 数量，保留两位小数
        order.setUnitPrice(totalPrice.divide(new BigDecimal(quantity), 2, java.math.RoundingMode.HALF_UP));
        order.setTotalPrice(totalPrice);
        order.setOrderTime(LocalDateTime.now());
        order.setStatus("PAID");

        int rows = orderMapper.insert(order);
        if (rows == 0) {
            // 订单插入失败，抛异常由事务回滚库存与余额
            throw new BusinessException("购票失败，请稍后重试");
        }

        // 购票成功，清除项目缓存（库存已变化）
        evictProjectCache();
        return "购票成功";
    }

    @Transactional(rollbackFor = Exception.class)
    public String cancelOrder(Integer orderId, Integer userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }

        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException("仅已支付的订单可以取消");
        }

        // 防止并发取消：原子更新状态
        int updated = orderMapper.updateOrderStatus(orderId, "CANCELLED", "PAID");
        if (updated == 0) {
            throw new BusinessException("订单状态已变更，操作失败");
        }

        // 原子恢复库存
        scenicProjectMapper.restoreStock(order.getProjectId(), order.getQuantity());

        // 原子恢复用户余额（单条 UPDATE，避免并发覆盖）
        userMapper.addBalance(userId, order.getTotalPrice());

        // 库存已变化，清除项目缓存
        evictProjectCache();
        return "取消成功";
    }

    /**
     * 用户提交退款申请（不直接退款）。
     * <p>
     * 退款必须经管理员审批：审批通过后才退回余额并恢复库存。
     * 原实现允许 COMPLETED（已消费）订单直接全额退款且不恢复库存，
     * 造成资金与库存双向流失，现改为审批制。
     * <p>
     * 订单状态 PAID/COMPLETED --&gt; REFUND_PENDING。
     */
    @Transactional(rollbackFor = Exception.class)
    public String applyRefund(Integer orderId, Integer userId, String applyReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }
        if (!"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException("仅已支付或已完成的订单可以申请退款");
        }

        // 防止重复申请：原子更新状态
        String originalStatus = order.getStatus();
        int updated = orderMapper.updateOrderStatus(orderId, "REFUND_PENDING", originalStatus);
        if (updated == 0) {
            throw new BusinessException("订单状态已变更，请勿重复提交");
        }

        RefundApplication application = new RefundApplication();
        application.setOrderId(orderId);
        application.setUserId(userId);
        application.setUsername(order.getUsername());
        application.setProjectName(order.getProjectName());
        application.setQuantity(order.getQuantity());
        application.setRefundAmount(order.getTotalPrice());
        application.setApplyReason(applyReason);
        application.setStatus("PENDING");
        refundApplicationMapper.insert(application);

        return "退款申请已提交，请等待管理员审核";
    }

    /**
     * 管理员审批退款申请。
     * <p>
     * 审批通过时：退回余额 + 恢复库存 + 订单置为 REFUNDED。
     * 审批拒绝时：订单从 REFUND_PENDING 还原为原状态（PAID/COMPLETED）。
     * <p>
     * 通过记录原状态，可以精确还原，避免拒绝后退款权限丢失。
     *
     * @param applicationId 申请 ID
     * @param action        APPROVED 或 REJECTED
     */
    @Transactional(rollbackFor = Exception.class)
    public String approveRefund(Integer applicationId, String action, String adminRemark, Integer adminId) {
        RefundApplication application = refundApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("退款申请不存在");
        }
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException("该申请已处理");
        }

        boolean approved = "APPROVED".equals(action);
        String newStatus = approved ? "APPROVED" : "REJECTED";

        // 防止并发审批：原子更新申请状态
        int updated = refundApplicationMapper.updateApplicationStatus(applicationId, newStatus, "PENDING");
        if (updated == 0) {
            throw new BusinessException("申请已被其他管理员处理，请刷新后再试");
        }

        // 单独更新审核字段，避免覆盖刚更新的状态
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RefundApplication> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", applicationId);
        wrapper.set("admin_id", adminId);
        wrapper.set("admin_remark", adminRemark);
        refundApplicationMapper.update(null, wrapper);

        if (approved) {
            // 订单 REFUND_PENDING -> REFUNDED（原子，防止重复退款）
            int orderUpdated = orderMapper.updateOrderStatus(
                    application.getOrderId(), "REFUNDED", "REFUND_PENDING");
            if (orderUpdated == 0) {
                throw new BusinessException("订单状态异常，退款失败");
            }

            // 退还余额（原子累加）
            int balanceUpdated = userMapper.addBalance(
                    application.getUserId(), application.getRefundAmount());
            if (balanceUpdated == 0) {
                throw new BusinessException("退款到账失败，请稍后重试");
            }

            // 恢复库存：审批通过即视为门票退回，库存 +1（按数量累加）
            scenicProjectMapper.restoreStock(
                    orderMapper.selectById(application.getOrderId()).getProjectId(),
                    application.getQuantity());

            // 库存已变化，清除项目缓存
            evictProjectCache();

            return "退款审批通过，已退款并恢复库存";
        }

        // 拒绝：把订单从 REFUND_PENDING 还原为 PAID（用户仍可重新申请或取消）
        orderMapper.updateOrderStatus(application.getOrderId(), "PAID", "REFUND_PENDING");
        return "已拒绝该退款申请";
    }

    public IPage<Order> getUserOrders(Integer userId, int current, int size, String projectName) {
        // 参数校验：current/size 必须 >= 1，避免 MyBatis-Plus 分页参数越界
        current = Math.max(current, 1);
        size = Math.max(size, 1);
        Page<Order> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);

        if (projectName != null && !projectName.trim().isEmpty()) {
            queryWrapper.like("project_name", projectName.trim());
        }

        queryWrapper.orderByDesc("order_time");
        return orderMapper.selectPage(page, queryWrapper);
    }

    public IPage<OrderVO> getUserOrdersVO(Integer userId, int current, int size, String projectName) {
        IPage<Order> page = getUserOrders(userId, current, size, projectName);
        return page.convert(this::toOrderVO);
    }

    public IPage<Order> getAllOrders(int current, int size, String projectName, String username) {
        // 参数校验：current/size 必须 >= 1
        current = Math.max(current, 1);
        size = Math.max(size, 1);
        Page<Order> page = new Page<>(current, Math.min(size, 100));
        List<Order> records;
        long total;

        if ((projectName != null && !projectName.trim().isEmpty()) ||
                (username != null && !username.trim().isEmpty())) {
            records = orderMapper.selectPageWithUserAndFilter(page, projectName, username);
            total = orderMapper.countOrdersWithFilter(projectName, username);
        } else {
            records = orderMapper.selectPageWithUser(page);
            total = orderMapper.countAllOrders();
        }

        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    public IPage<OrderVO> getAllOrdersVO(int current, int size, String projectName, String username) {
        IPage<Order> page = getAllOrders(current, size, projectName, username);
        return page.convert(this::toOrderVO);
    }

    public Order getOrderById(Integer orderId, String userRole, Integer currentUserId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }

        if (!roleLevelUtil.isAdmin(userRole) && !order.getUserId().equals(currentUserId)) {
            return null;
        }

        return order;
    }

    public OrderVO getOrderByIdVO(Integer orderId, String userRole, Integer currentUserId) {
        Order order = getOrderById(orderId, userRole, currentUserId);
        return order != null ? toOrderVO(order) : null;
    }

    private OrderVO toOrderVO(Order order) {
        return OrderVO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .username(order.getUsername())
                .projectId(order.getProjectId())
                .projectName(order.getProjectName())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .totalPrice(order.getTotalPrice())
                .orderTime(order.getOrderTime())
                .status(order.getStatus())
                .build();
    }

    // ==================== 退款申请查询 ====================

    /** 查询某用户自己的退款申请 */
    public IPage<RefundApplicationVO> getUserRefundApplications(
            Integer userId, int current, int size) {
        current = Math.max(current, 1);
        size = Math.max(size, 1);
        Page<RefundApplication> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<RefundApplication> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("create_time");
        return refundApplicationMapper.selectPage(page, qw).convert(this::toRefundVO);
    }

    /** 查询待审批的退款申请（管理员） */
    public IPage<RefundApplicationVO> getPendingRefundApplications(
            int current, int size, String username) {
        current = Math.max(current, 1);
        size = Math.max(size, 1);
        Page<RefundApplication> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<RefundApplication> qw = new QueryWrapper<>();
        qw.eq("status", "PENDING");
        if (username != null && !username.trim().isEmpty()) {
            qw.like("username", username.trim());
        }
        qw.orderByDesc("create_time");
        return refundApplicationMapper.selectPage(page, qw).convert(this::toRefundVO);
    }

    private RefundApplicationVO toRefundVO(RefundApplication app) {
        return RefundApplicationVO.builder()
                .id(app.getId())
                .orderId(app.getOrderId())
                .userId(app.getUserId())
                .username(app.getUsername())
                .projectName(app.getProjectName())
                .quantity(app.getQuantity())
                .refundAmount(app.getRefundAmount())
                .applyReason(app.getApplyReason())
                .status(app.getStatus())
                .adminRemark(app.getAdminRemark())
                .createTime(app.getCreateTime())
                .updateTime(app.getUpdateTime())
                .build();
    }
}
