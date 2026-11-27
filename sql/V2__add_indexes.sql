-- 数据库迁移脚本 V2
-- 日期: 2026-07-05
-- 变更:
--   1. 为 t_order 添加复合索引优化查询
--   2. 为 t_user 添加手机号索引

ALTER TABLE t_order ADD INDEX idx_user_status (user_id, status);
ALTER TABLE t_user ADD INDEX idx_phone (phone);
