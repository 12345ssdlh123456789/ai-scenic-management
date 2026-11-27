-- VIP 过期自动降级功能：给 t_user 表添加 vip_expire_time 字段
-- 执行方式: mysql -u root -p scenic_db < migration_vip_expiry.sql

ALTER TABLE t_user
    ADD COLUMN vip_expire_time DATETIME NULL COMMENT 'VIP过期时间' AFTER balance;

-- 如果已有 VIP 用户，为其设置默认过期时间（从当前时间起 30 天）
-- 这个 SQL 在第一次部署时执行，后续新 VIP 由代码自动设置
UPDATE t_user SET vip_expire_time = DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE role = 'VIP' AND vip_expire_time IS NULL;