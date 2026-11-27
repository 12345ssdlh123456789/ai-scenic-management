-- 数据库迁移脚本 V4
-- 日期: 2026-10-12
-- 变更:
--   1. 新增 t_refund_application 表（退款审批流）
--   2. 修复 V3 重复索引问题（idx_user_order_time 已在 schema.sql 定义）
--   3. 为 t_user.role 添加索引（被 VipExpiryTask 与用户列表过滤使用）
--
-- 背景: 原退款逻辑允许 COMPLETED（已消费）订单全额退款且不恢复库存，
--       导致资金与库存双向流失。现改为"申请 -> 管理员审批 -> 通过后退款+恢复库存"。

CREATE TABLE IF NOT EXISTS t_refund_application (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_id INT NOT NULL COMMENT '订单ID',
    user_id INT NOT NULL COMMENT '申请用户ID',
    username VARCHAR(50) COMMENT '用户名(冗余)',
    project_name VARCHAR(100) COMMENT '项目名称(冗余)',
    quantity INT NOT NULL COMMENT '退款门票数量',
    refund_amount DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    apply_reason VARCHAR(500) COMMENT '退款理由',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    admin_id INT COMMENT '审核管理员ID',
    admin_remark VARCHAR(500) COMMENT '审核备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_username (username)
) ENGINE=InnoDB COMMENT='退款申请表';

-- 为 t_user.role 添加索引（若不存在）
-- 被 VipExpiryTask 的 role 过滤和用户列表查询使用
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_user' AND index_name = 'idx_role'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_user ADD INDEX idx_role (role)',
    'SELECT "idx_role already exists" AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
