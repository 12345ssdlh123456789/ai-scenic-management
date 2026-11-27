-- 为 t_order 表添加复合索引优化用户订单查询
ALTER TABLE t_order ADD INDEX idx_user_order_time (user_id, order_time);

-- 为 t_scenic_project 表确保软删除过滤正常工作（MyBatis-Plus @Where 自动处理）
-- 无需额外 SQL，注解已配置

-- 为 t_user 表确保软删除过滤正常工作
-- 无需额外 SQL，注解已配置

-- 为 t_recharge_application 表确保过滤正常工作
-- 无需额外 SQL，注解已配置

-- 为 t_vip_application 表确保过滤正常工作
-- 无需额外 SQL，注解已配置