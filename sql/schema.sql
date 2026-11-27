-- 景区管理系统数据库初始化脚本
-- 执行方式: mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS scenic_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE scenic_db;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    role VARCHAR(20) DEFAULT 'USER' COMMENT '角色: USER/VIP/ADMIN/SUPER_ADMIN',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-正常 0-禁用',
    balance DECIMAL(10,2) DEFAULT 0.00 COMMENT '账户余额',
    vip_expire_time DATETIME NULL COMMENT 'VIP过期时间',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='用户表';

-- 景点项目表（添加 version 字段支持乐观锁）
CREATE TABLE IF NOT EXISTS t_scenic_project (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    price DECIMAL(10,2) NOT NULL COMMENT '票价',
    stock INT DEFAULT 0 COMMENT '库存',
    description TEXT COMMENT '项目描述',
    image_url VARCHAR(500) COMMENT '图片链接',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-上架 0-下架',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='景点项目表';

-- 订单表（添加索引）
CREATE TABLE IF NOT EXISTS t_order (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '用户ID',
    project_id INT NOT NULL COMMENT '项目ID',
    project_name VARCHAR(100) COMMENT '项目名称(冗余)',
    quantity INT NOT NULL COMMENT '数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    total_price DECIMAL(10,2) NOT NULL COMMENT '总价',
    order_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    status VARCHAR(20) DEFAULT 'PAID' COMMENT '订单状态: PAID/CANCELLED/REFUNDED/COMPLETED',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_user_id (user_id),
    INDEX idx_order_time (order_time),
    INDEX idx_status (status),
    INDEX idx_project_name (project_name),
    INDEX idx_user_order_time (user_id, order_time)
) ENGINE=InnoDB COMMENT='订单表';

-- 充值申请表（添加索引）
CREATE TABLE IF NOT EXISTS t_recharge_application (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名(冗余)',
    amount DECIMAL(10,2) NOT NULL COMMENT '充值金额',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    admin_id INT COMMENT '审核管理员ID',
    admin_remark VARCHAR(500) COMMENT '审核备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_username (username)
) ENGINE=InnoDB COMMENT='充值申请表';

-- VIP申请表（添加索引）
CREATE TABLE IF NOT EXISTS t_vip_application (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名(冗余)',
    apply_reason VARCHAR(500) COMMENT '申请理由',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    admin_id INT COMMENT '审核管理员ID',
    admin_remark VARCHAR(500) COMMENT '审核备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_username (username)
) ENGINE=InnoDB COMMENT='VIP申请表';

-- 退款申请表
-- 退款必须经管理员审批：审批通过后才退回余额并恢复库存。
-- 这样可避免"已消费订单全额退款且不回收库存"的资金/库存双漏。
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

-- AI 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL COMMENT '分类',
    question VARCHAR(500) NOT NULL COMMENT '典型问题',
    answer TEXT NOT NULL COMMENT '标准答案',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category)
) ENGINE=InnoDB COMMENT='AI知识库表';

-- 操作日志表（审计）
CREATE TABLE IF NOT EXISTS t_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT COMMENT '操作用户ID',
    username VARCHAR(50) COMMENT '操作用户名',
    module VARCHAR(50) COMMENT '操作模块',
    action VARCHAR(50) COMMENT '操作动作',
    description VARCHAR(500) COMMENT '操作描述',
    request_method VARCHAR(20) COMMENT '请求方法',
    request_params TEXT COMMENT '请求参数(JSON)',
    response_result VARCHAR(20) COMMENT '响应结果',
    ip_address VARCHAR(50) COMMENT '操作IP',
    duration BIGINT COMMENT '耗时(ms)',
    operate_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_operate_time (operate_time),
    INDEX idx_module (module)
) ENGINE=InnoDB COMMENT='操作日志表';

-- 初始知识库数据
INSERT INTO t_knowledge_entry (category, question, answer) VALUES
('开放时间', '园区几点开门关门？', '景区开放时间为每天8:00-18:00（夏季至18:30），最后入园时间为闭园前1小时。节假日正常开放。'),
('开放时间', '什么时候去人最少？', '工作日上午9:00-10:00和下午15:00以后人相对较少。周末和节假日人流较大，建议错峰出行。'),
('开放时间', '晚上有开放吗？', '景区夜间不开放，但部分季节会有夜场活动，请关注官方公告。'),
('票价', '门票多少钱？', '成人票120元/人，儿童票/学生票60元/人（需出示有效证件），60岁以上老人和1.2米以下儿童免票。'),
('票价', 'VIP有什么优惠？', 'VIP会员享受门票8折、项目免费体验3次、优先排队等权益。可在站内申请VIP。'),
('票价', '可以网上提前买票吗？', '可以在小程序和官网提前购票，线上票价比现场便宜10元。支持在线退改。'),
('票价', '有套票优惠吗？', '有！三大项目联票200元（原价230元），全天畅玩票280元，含所有项目和一次免费餐饮。'),
('景点介绍', '有哪些好玩的项目？', '我们有过山车、摩天轮、海盗船、旋转木马四大核心项目，还有亲子乐园和美食街。'),
('景点介绍', '过山车刺激吗？', '过山车是我们的招牌项目，最高时速120km/h，全程约2分钟。恐高和心脏病患者不建议体验。'),
('景点介绍', '摩天轮能看到多远？', '摩天轮高度88米，晴天能远眺15公里外的城市全景，非常适合拍照打卡。'),
('景点介绍', '适合带小孩去玩吗？', '非常适合！旋转木马和亲子乐园专为儿童设计，有专门的工作人员看护。1.2米以上项目都可体验。'),
('交通', '怎么到达景区？', '地铁3号线直达景区站B出口；自驾可导航至景区停车场，停车费首小时免费，之后5元/小时。'),
('交通', '有停车场吗？', '有大型停车场，共500个车位。节假日建议早点来，10点后通常停满。'),
('注意事项', '可以带宠物吗？', '景区内不允许携带宠物入园，如有需要可存放在门口宠物寄存处（免费）。'),
('注意事项', '能带食物进去吗？', '可以带少量零食和水，景区内也有多家餐厅和小吃摊。禁止携带酒精饮料和玻璃瓶装饮品。'),
('注意事项', '下雨天还开放吗？', '雨天正常开放，部分户外项目可能暂停。我们会免费提供雨衣。'),
('注意事项', '有储物柜吗？', '入口处和各个项目旁都有储物柜，普通柜10元/天，大号柜20元/天，支持扫码支付。');

-- 用户表（密码是 BCrypt 加密后的 "123456"）
INSERT INTO t_user (username, password, role, phone, email, status, balance) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'SUPER_ADMIN', '13800000000', 'admin@example.com', 1, 10000.00),
('testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'USER', '13800000001', 'user@example.com', 1, 100.00);

INSERT INTO t_scenic_project (name, price, stock, description, image_url, status) VALUES
('过山车', 80.00, 100, '经典刺激项目，适合年轻人', '/images/roller-coaster.jpg', 1),
('摩天轮', 60.00, 200, '浪漫高空观景，适合情侣', '/images/ferris-wheel.jpg', 1),
('海盗船', 50.00, 150, '摇摆刺激，全家适用', '/images/pirate-ship.jpg', 1),
('旋转木马', 40.00, 300, '儿童友好项目', '/images/merry-go-round.jpg', 1);
