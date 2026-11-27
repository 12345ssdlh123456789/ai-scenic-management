# 景区管理系统

Spring Boot 3.2 + Vue 3 的景区运营后台：管理员管景区项目、订单、充值/退款、VIP 申请与操作日志，
游客端可以浏览项目、下单、充值、申请 VIP，并有一个带限流的 AI 智能客服。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.2 · Spring Security + JWT · MyBatis-Plus 3.5.5 |
| 数据 | MySQL 8（Druid 连接池）· Redis（缓存 / 限流） |
| 前端 | Vue 3 · Vite · Vue Router · Pinia · Axios |
| 文档 | Springdoc（OpenAPI 3，`/swagger-ui/index.html`） |
| 部署 | Dockerfile + docker-compose + Nginx |

## 功能模块

- **认证与权限**：JWT 登录，管理员 / 普通用户两套视图，接口级 `@PreAuthorize`
- **景区项目**：增删改查、上下架、图片与价格
- **订单**：下单、支付状态流转、全部订单与我的订单
- **充值 / 退款**：申请、审批、历史记录
- **VIP**：申请、审批、到期时间处理
- **操作日志**：关键动作落库，管理员可查
- **AI 智能客服**：NVIDIA 兼容接口（`/v1/ai`），带限流与知识库上下文，未配 Key 时该功能自动不可用，其余功能不受影响

## 本地运行

前置：JDK 17+、Maven（仓库自带 `mvnw`）、Node 18+、MySQL 8、Redis。

```bat
REM 1. 建库并导入表结构
mysql -u root -p -e "CREATE DATABASE scenic CHARACTER SET utf8mb4"
mysql -u root -p scenic < sql\schema.sql

REM 2. 配置环境变量（密钥全部走 .env，不入库）
copy .env.example .env
REM 编辑 .env：DB_PASSWORD / JWT_SECRET / NVIDIA_API_KEY / REDIS_*

REM 3. 后端（默认 9090）
call env.bat
mvnw.cmd spring-boot:run

REM 4. 前端（默认 5173）
cd myvue
npm install
npm run dev
```

也可以直接双击 `start.bat`（会调 `env.bat` 加载 `.env` 再启后端）。

## 配置说明

所有敏感配置都从环境变量读取，仓库里只有 `.env.example` 占位符：

```
DB_PASSWORD=CHANGE_ME_DB_PASSWORD
JWT_SECRET=CHANGE_ME_GENERATE_A_RANDOM_SECK
NVIDIA_API_KEY=your-nvidia-api-key-here
```

`.env` 已被 `.gitignore` 排除。`env.bat` 只负责把 `.env` 里的值导入当前 cmd 会话，本身不包含任何密钥。

## 目录

```
src/main/java/com/example/mpdemo/
  controller/    REST 接口（项目 / 订单 / 充值 / VIP / 用户 / 日志 / AI）
  service/       业务逻辑
  mapper/        MyBatis-Plus Mapper
  config/        安全、限流、Swagger
sql/             表结构与增量迁移
myvue/           Vue 3 前端
.shots/          界面截图（验收用）
```

## 说明

课程/个人项目，未做生产级压测；AI 客服依赖外部模型服务，未配 Key 时自动降级，不影响主流程。
