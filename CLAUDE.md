# 景区管理系统 (dazuoye)

## Tech Stack
- **Backend:** Spring Boot 3.2.0, Java 17, MyBatis-Plus 3.5.5, Spring Security (BCrypt), Druid, MySQL, Redis, server.port=9090
- **Frontend:** Vue 3 + Vite (port 5173), Element Plus, Axios, Vue Router 5, Pinia
- **Base URL:** Environment-based via `.env.development` / `.env.production` (VITE_API_BASE_URL)
- **CORS:** Restricted to non-actuator paths only (SecurityConfig.java)
- **Cache:** Redis (Spring Cache abstraction with @Cacheable/@CacheEvict)
- **Auth:** JWT (access token 24h + refresh token 7d)
- **Rate Limiting:** Redis-based (configurable per-endpoint via ScenicProperties)
- **Audit:** Async operation logging to t_operation_log

## Project Structure
```
src/main/java/com/example/mpdemo/
├── controller/   — BaseController (shared), OrderController (+退款审批), ScenicProjectController,
│                   UserController, RechargeController, VipApplicationController,
│                   AIChatController, OperationLogController
├── converter/    — UserConverter (MapStruct)。⚠️ 必须与 mapper/ 分开，
│                   否则被 @MapperScan 当作 MyBatis mapper 覆盖
├── service/      — OrderService (@Transactional purchaseTicket + atomic status update),
│                   RechargeService, VipApplicationService, ScenicProjectService
│                   (softDeleteProject), LoginAttemptService (Redis-backed),
│                   AIChatService, OperationLogService
├── entity/       — User, Order, ScenicProject, VipApplication, RechargeApplication,
│                   KnowledgeEntry, OperationLog, Result, LoginRequest, BusinessException
├── dto/          — CreateUserDTO, UpdateUserDTO, PurchaseTicketDTO, RechargeApplyDTO,
│                   ApproveRechargeDTO, ApplyVipDTO, ApproveVipDTO, ChatRequestDTO,
│                   KnowledgeDTO, ChangePasswordDTO, RefreshTokenDTO,
│                   CreateProjectDTO, UpdateProjectDTO
├── vo/           — LoginVO, UserVO, UserListVO (no balance), OrderVO, ProjectVO (has `deleted` field),
│                   RechargeApplicationVO, VipApplicationVO
├── mapper/       — UserMapper, OrderMapper (selectPageWithUser/selectPageWithUserAndFilter + updateOrderStatus),
│                   ScenicProjectMapper (selectAvailablePage/selectAllPage + deductStock + restoreStock),
│                   VipApplicationMapper, RechargeApplicationMapper, RefundApplicationMapper,
│                   KnowledgeMapper, OperationLogMapper
├── config/       — SecurityConfig, MyBatisPlusConfig, AIConfig, OpenApiConfig (conditional on prod),
│                   RedisConfig, RateLimiter (with local fallback), TokenBlacklist (Redis Hash + SHA-256),
│                   ScenicProperties (config-driven, @PostConstruct init), IdempotencyService,
│                   ClientIpFilter
├── filter/       — JwtAuthenticationFilter, JwtDetails
├── exception/    — GlobalExceptionHandler
└── util/         — JwtUtil (validates secret length >= 32), RoleLevelUtil (config-driven),
                    HttpUtil (unified getClientIp, single source of truth)

myvue/src/
├── stores/       — user.js (Pinia: token, role, auth state; clearLoginData now clears refreshToken)
├── views/        — LoginView, ProjectView, MyOrderView (fixed getCurrentOrderStatus), AllOrderView,
│                   UserView, ProfileView, VipApplyView, VipApplicationView,
│                   RechargeHistoryView, RechargeApplicationView, RefundApplicationView,
│                   AIChatView, OperationLogView
├── api/          — user.js, order.js, project.js, recharge.js, vip.js, ai.js
├── utils/        — request.js (env-based baseURL, token refresh with pending request queue),
│                   format.js (formatMoney/firstLetter/formatDateTime — null 安全)
├── router/       — index.js (route guards for auth/role)
└── main.js       — Pinia, ElementPlus, Router setup
├── .env.development — VITE_API_BASE_URL=http://localhost:9090
└── .env.production  — VITE_API_BASE_URL=/api

sql/              — schema.sql (含 t_refund_application)
                    V2/V3/V4 迁移脚本
```

## Role Hierarchy
USER(1) -> VIP(2) -> ADMIN(3) -> SUPER_ADMIN(4)
- RoleLevelUtil controls who can view/edit/delete whom (config-driven via ScenicProperties)
- Admin routes: /users, /allOrders, /vipApplications, /rechargeApplications, /operationLogs

## Key Improvements (2026-07-03)
- **Redis Integration:** Cache manager with TTL per cache name, RedisTemplate for programmatic access
- **DTO/VO Layer:** All entities separated from API contracts — no entity leaks to frontend
- **JWT Refresh Token:** Dual-token auth (access 24h + refresh 7d), automatic refresh on 401
- **Rate Limiting:** Redis-based login rate limiter (20 req/min per IP)
- **Operation Audit Log:** Async logging of all admin operations (module, action, IP, duration)
- **Frontend Pinia:** Centralized state management replacing localStorage scattered access
- **Token Auto-Retry:** Axios interceptor queues requests during token refresh
- **JUnit 5 Tests:** LoginAttemptService, RoleLevelUtil, JwtUtil, OrderService unit tests
- **Docker Compose:** MySQL + Redis + Backend + Frontend one-command startup

## Key Improvements (2026-07-05) — Bug Fixes & Hardening

### P0 Critical Fixes
1. **购票超卖竞态条件修复** — `OrderService.purchaseTicket()` 移除了 `@Transactional` + 错误的悲观锁。改为纯乐观锁方案：通过原子 `UPDATE ... WHERE stock >= #{quantity}` 保证库存一致性，前置 `selectById` 不再参与锁竞争。购票失败时自动回滚库存。Mapper 方法从 `deductStockWithLock` 重命名为 `deductStock`。

2. **Token 黑名单内存泄漏修复** — `TokenBlacklist` 从每个 token 一个 Redis key 改为 Redis Set 方案（`token:blacklist:set`），所有黑名单 token hash 存储在单个 key 中，附带 TTL 自动清理。新增 `cleanupExpired()` 方法供定时任务调用。

3. **Swagger 生产环境隐藏** — `OpenApiConfig` 添加 `@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")`，`application.properties` 设置 `springdoc.api-docs.enabled=false`，生产环境默认不暴露 API 文档。

### P1 Significant Fixes
4. **MyOrderView getCurrentOrderStatus() 未定义** — 模板中 `v-if="['PAID', 'COMPLETED'].includes(getCurrentOrderStatus())"` 引用了不存在的函数。改为 `canShowActions()` 方法，检查订单列表中是否存在可操作状态的订单。

5. **软删除项目查询** — `ProjectVO` 新增 `deleted` 布尔字段（`status == 0` 时为 true），前端可明确区分已下架项目。`ScenicProjectService.toProjectVO()` 设置该字段。

6. **CORS 路径限制** — `SecurityConfig` 中 CORS 从 `/**` 改为仅对业务 API 路径（`/user/**`, `/order/**`, `/project/**` 等）生效，排除 `/actuator/**` 等内部端点。

7. **JWT secret 空值保护** — `JwtUtil` 构造函数增加校验：secret 不能为空、不能为空白字符串、长度必须 >= 32 字符，否则启动时抛出 `IllegalArgumentException`。

8. **clearLoginData 清理 refreshToken** — `user.js` 的 `clearLoginData()` 现在同步清除 `localStorage.removeItem('refreshToken')`，避免登出后残留过期凭证。

### P2 Code Quality Improvements
9. **统一 Controller/Service 层模式** — 新增 `BaseController` 抽象基类，提供 `getJwtDetails()`、`getClientIp()`、`logOperation()` 三个通用方法。`OrderController`、`RechargeController`、`VipApplicationController`、`ScenicProjectController`、`AIChatController` 均继承 `BaseController`，消除了每个 Controller 中重复的 IP 提取和日志记录代码。

10. **提取 getClientIp() 工具类** — 新增 `HttpUtil.getClientIp()` 统一 IP 提取逻辑（优先级：request attribute → X-Forwarded-For → Proxy-Client-IP → WL-Proxy-Client-IP → X-Real-IP → remoteAddr）。所有 Controller 委托给该工具类。

11. **配置化 VIP 折扣和角色等级** — 新增 `ScenicProperties` 配置类（`@ConfigurationProperties(prefix = "scenic")`），支持从 `application.properties` 配置 `scenic.vip-discount`、`scenic.role-levels`、`scenic.login-max-attempts` 等。`OrderService`、`ScenicProjectService`、`LoginAttemptService`、`RoleLevelUtil` 全部改为从配置读取，不再硬编码魔法值。

12. **前端环境变量 baseURL** — 创建 `.env.development` 和 `.env.production`，`request.js` 使用 `import.meta.env.VITE_API_BASE_URL` 动态设置 baseURL，区分开发和生产环境。

13. **单元测试适配** — `OrderServiceTest` 中所有 `deductStockWithLock` mock 调用更新为 `deductStock`。

## Key Improvements (2026-07-19) — Security Audit & Hardening

### P0 Critical Fixes
1. **Token 黑名单 SHA-256 防碰撞** — `TokenBlacklist` 从 `Integer.toHexString(token.hashCode())` 改为 **SHA-256** 哈希，Redis Set 改为 **Redis Hash**（key=tokenHash, value=expireTimestamp），每个 token 独立 TTL，彻底消除 hashCode 碰撞导致的误杀拒登。同时加了 `@Autowired(required=false)` 容错，Redis 宕机不阻断启动。

2. **RateLimiter Redis 宕机降级** — `RateLimiter` 的 `redisTemplate` 改为 `@Autowired(required=false)`，Redis 不可用时**自动降级为本地内存限流**（`LocalCounter` 内部类），保证服务可用性。

3. **角色权限初始化** — `ScenicProperties.initDefaults()` 添加 **`@PostConstruct`**，Spring 启动时自动填充 `roleLevels` 映射表。修复了 `canView()` 永远返回 true（0 >= 0）导致后台权限控制完全失效的问题。

### P1 Significant Fixes
4. **购票事务完整性** — `OrderService.purchaseTicket()` 添加 **`@Transactional`**，余额检查和扣减在同一事务内。购票失败时**同时回滚余额和库存**（之前只回滚了库存）。余额不足时也会回滚已扣库存。

5. **库存恢复原子化** — `cancelOrder` 和 `refundOrder` 中的库存恢复从 `selectById → setStock → updateById` 两步走改为 `ScenicProjectMapper.restoreStock()` **原子 UPDATE**（`SET stock = stock + #{quantity}`），杜绝并发丢失更新。

6. **乐观锁 @Version 注解** — `ScenicProject.version` 和 `Order.version` 字段添加 **`@Version`** 注解，使 MyBatis-Plus 的 `OptimisticLockerInnerInterceptor` 真正生效。

7. **项目 CRUD 使用 DTO** — `ScenicProjectController` 的 `addProject`/`updateProject` 从直接接收 `@RequestBody ScenicProject` 改为接收 **`CreateProjectDTO`/`UpdateProjectDTO`**（带 `@Valid` 校验），Service 层强制清空 id/version/createTime/updateTime，防止前端篡改敏感字段。

8. **前端审批 API 路径修复** — `VipApplicationView.vue` 和 `RechargeApplicationView.vue` 中 `request.post()` 路径统一添加 **`/` 前导斜杠**，修复了相对路径在不同路由下请求地址错误的问题。

9. **Token 刷新失败 pending 请求清理** — `request.js` 中 refresh 失败时**先 reject 所有 pending 请求**再跳转登录页，避免未处理的 promise 拒绝。

10. **订单状态并发安全** — `cancelOrder` 和 `refundOrder` 使用 `OrderMapper.updateOrderStatus()` **原子状态更新**（`WHERE id=? AND status=?`）防止重复取消/退款。

### P2 Code Quality Improvements
11. **LoginAttemptService 迁移 Redis** — 从 `ConcurrentHashMap` 内存存储改为 **Redis 存储**（`login:attempts:{user}` + `login:lock:{user}`），支持多节点部署，TTL 自动过期。

12. **IP 提取逻辑去重** — `ClientIpFilter.extractIp()` 删除，统一委托 `HttpUtil.getClientIp()`，消除重复代码。

13. **错误码语义修正** — `Result.error()` 和 `BusinessException` 默认状态码从 **500 改为 400**，符合 RESTful 规范（500 应仅用于服务端异常）。

14. **用户列表隐私隔离** — 新增 `UserListVO`（不含 balance），`UserController.findAll` 返回 `UserListVO` 而非 `UserVO`，防止管理员列表接口泄露用户余额。

15. **软删除命名修正** — `ScenicProjectService.deleteProject()` 重命名为 **`softDeleteProject()`**，Controller 端点不变（DELETE /project/{id}），但方法名准确反映"下架"语义。

---

## Key Improvements (2026-10-12) — 全面审查与修复（第四轮）

> 本轮由 AI 代理执行：先建立 Git 基线，再分四批修复，**每一步都用运行时实测验证**，
> 而非仅凭静态推理。基线下述。

### 方法论要点（重要教训）

1. **先做运行时验证，再动手改。** 本轮一开始通过静态推理得出"`@PostConstruct` 早于
   `@ConfigurationProperties` 绑定，导致角色等级全为 0、权限模型失效"的结论——**实测推翻**：
   ```
   roleLevels = {SUPER_ADMIN=4, ADMIN=3, USER=1, VIP=2}   ← 绑定完全正常
   isSuperAdmin(ADMIN) = false                             ← 正确拒绝
   ```
   若照此"修复"，会改坏一段本来正确的代码。**框架生命周期语义必须实测。**

2. **但同时，静态推理能发现实测容易漏掉的问题。** 反向的例子是
   `logic-delete-field: status`：一开始推理认为 `.properties` 优先级更高所以未生效，
   实测发现**它确实生效了**，且已造成 10 条订单全部不可见。

3. **`application.yml` 与 `application.properties` 共存时的属性合并行为不可靠推理**，必须实测。

### P0 级修复

#### 0. Redis 缓存命中即 500（启动后手动验收时发现）

**症状**：`GET /v1/project/list` **第 1 次 200，第 2 次起 500**。删除 Redis key 后
稳定复现 `200 → 500 → 500`。

**根因**：`RedisConfig` 把**关闭了 DefaultTyping** 的 `safeObjectMapper()`
传给了 `GenericJackson2JsonRedisSerializer`。该序列化器依赖 JSON 中的 `@class`
字段还原具体类型；关闭 DefaultTyping 后：

- 写入时不含 `@class`
- 读取时无法还原类型，只能得到 `LinkedHashMap`
- Spring 把它当作方法声明的返回类型（`Result`）返回 → `ClassCastException` → 500

**实测证据**（`CacheRoundTripTest`）：`cache.put(key, Result)` 后
`cache.get(key)` 得到的是 `java.util.LinkedHashMap`。

**修复**：新增 `RedisConfig.typedObjectMapper()`，用
`BasicPolymorphicTypeValidator` **白名单**（`com.example.mpdemo.` / `java.util.` /
`java.lang.` / `java.time.` / `java.math.`）开启 DefaultTyping —— 既恢复类型还原，
又不引入任意类实例化的反序列化攻击面。修复后缓存值形如：
```json
{"@class":"com.example.mpdemo.entity.Result","code":200,...}
```

> ⚠️ 修复后需**清空旧缓存**（旧条目没有 `@class`，在 TTL 到期前仍会 500）：
> `redis-cli --scan --pattern "projects*" | xargs redis-cli del`

**启动验收时发现，静态审查与单元测试都没抓到** —— 因为此前没有任何测试
真正走「写入缓存再读回」这条路径。

#### 1. 全局 `logic-delete-field: status` —— 订单全部不可见（已实测确认）

`application.yml` 配置了 `mybatis-plus.global-config.db-config.logic-delete-field: status`。
这是**全局字段名配置**，MyBatis-Plus 会给**所有含 `status` 属性的实体**追加过滤条件。
但本项目里 `status` 是**业务状态字段**（`t_order.status` 存 `PAID`/`CANCELLED`/...），
不是删除标记。

实测 SQL：
```sql
-- 修复前
SELECT ... FROM t_order WHERE status='1'    → 0 行（数据库实际有 10 条 PAID 订单）
-- 修复后
SELECT ... FROM t_order                      → 10 行 ✅
```

**影响**：`myOrders`/`allOrders` 永远返回空、`selectById` 永远 null、
取消/退款全部报"订单不存在"。购票是**单向黑洞**：扣钱扣库存写入订单，但再也读不出来。

**修复**：从配置中删除该三项。若确需软删除，用 `@TableLogic` 按实体显式标注。

> 注意 `V3__add_soft_delete_and_indexes.sql` 注释声称"注解已配置"，但全库 grep
> **零个** `@TableLogic`/`@Where`——该注释是假的。

#### 2. 双配置文件冲突 → 缓存后端不确定

`application.yml` 与 `application.properties` **同时存在**，且对同一属性给出不同值：
```
yml:        spring.cache.type: redis
properties: spring.cache.type=simple
```
**修复**：删除 `application.yml`，`application.properties` 成为唯一配置源。
按需求**明确使用 Redis**（`spring.cache.type=redis`），`RedisConfig` 中按缓存名配置的
TTL 策略（projects 1h / users 10min / knowledge 2h）现在真正生效。

补回原本只存在于 yml 的配置项：`scenic.vip-valid-days`、`async.executor.*`。

#### 3. 缓存 key 不匹配 → 持续返回过期库存

读路径的 key 是动态的（`list:<current>:<size>:<name>` 和 `detail:<id>`），
但 `evictProjectCache()` 硬编码 `cache.evict("list:1:10:")`：
- 带搜索/翻页的缓存**永不清除**
- **`detail:<id>` 全库没有任何一处 evict** → 购票后详情页库存可陈旧最长 1 小时

**修复**：两处 `evictProjectCache()` 改为 `cache.clear()` 整体清空
（购票/取消/退款非高频操作，代价可接受）。

#### 4. 退款审批流（新功能，按需求实现）

**需求**：退款需管理员审批；审批通过后**退钱，且门票库存+N**。

原实现允许 `COMPLETED`（已消费）订单**直接全额退款且不恢复库存** —— 资金与库存双漏。

**新增流程**：
```
PAID/COMPLETED --用户申请--> REFUND_PENDING --管理员通过--> REFUNDED（退钱 + 库存+N）
                                            --管理员拒绝--> 还原为 PAID（不动钱和库存）
```
- 新增表 `t_refund_application`（含 `sql/V4__refund_application.sql` 迁移）
- 新增 `RefundApplication` / `RefundApplicationMapper` / `ApplyRefundDTO` /
  `ApproveRefundDTO` / `RefundApplicationVO` / `RefundApplicationView.vue`
- `OrderStatus` 新增 `REFUND_PENDING`
- 接口：`POST /v1/order/{id}/refund`（申请）、`GET /v1/order/myRefunds`、
  `GET /v1/order/pendingRefunds`（管理员）、`POST /v1/order/approveRefund`（管理员）
- 前端新增"退款审核"页面与菜单入口

**实测验证**（真实 HTTP + 数据库核对）：

| 项目 | 前 | 后 | 期望 | 结果 |
|---|---|---|---|---|
| 订单状态 | PAID | REFUNDED | REFUNDED | ✅ |
| 项目库存 | 0 | **10** | 0+10 | ✅ |
| 用户余额 | 460.00 | **500.00** | 460+40 | ✅ |

重复审批被拒绝（400），且钱与库存不再变动。

#### 5. 提权漏洞：ADMIN 可自提为 SUPER_ADMIN

`UpdateUserDTO.role` 存在，`updateEntity` **未 ignore `role`**，而超管校验只写在
`save()` 里，`update()` 完全没有 → 任何 ADMIN 发
`PUT /v1/user {"id":<自己>,"role":"SUPER_ADMIN"}` 即可提权。

**修复**：
- `RoleLevelUtil` 新增 `isValidRole()` 与 `canManageRole()`（要求调用者 ≥ ADMIN 且等级不低于目标）
- `update()` 与 `save()` 都校验角色合法性与可授予性
- 实测：自我提升 / 创建超管 / 非法角色值 三种攻击**全部 400 拒绝**

#### 6. 🔴 MapStruct 接口被当作 MyBatis mapper 扫描 —— 用户管理完全不可用

**本轮最隐蔽的 bug，前三轮均未发现。**

`@MapperScan("com.example.mpdemo.mapper")` 使 MyBatis 把该包下**所有接口**注册为
mapper，其中包含 MapStruct 的 `UserMapperStruct`，其生成的 `@Component` 实现被
MyBatis 代理覆盖，调用即抛：

```
BindingException: Invalid bound statement (not found):
  com.example.mpdemo.mapper.UserMapperStruct.toEntity
```

**影响**：`POST /v1/user`（创建用户）及所有走 `toVO`/`toListVO` 的接口全部 500
（"系统繁忙"）。**用户管理功能实际完全不可用。**

**修复**：MapStruct 接口迁出 MyBatis 扫描范围，新建
`com.example.mpdemo.converter.UserConverter`，并更新 `UserController`。

> **教训**：MapStruct (`@Mapper`) 与 MyBatis (`@Mapper`) 注解同名，
> 且都倾向放在 `mapper` 包。**务必把 MapStruct 放在独立包**，否则 `@MapperScan`
> 会静默覆盖其实例。

#### 7. Token 刷新链路完全失效（前端路径错误）

`request.js` 请求 `/user/refreshToken`，后端实际是 `/v1/user/refreshToken` → 404
→ 走 catch → 静默登出。**access token 过期后用户必被踢且无法续期。**

**修复**：补上 `/v1` 前缀。并适配轮换后的返回结构 `{ token, refreshToken }`。

#### 8. 前端 4 个页面因引用不存在的图标而白屏

| 文件 | 错误 | 后果 |
|---|---|---|
| `AllOrderView.vue:218` | 用了 `CheckCircle`（不存在，应为 `CircleCheck`） | 管理员订单页白屏 |
| `MyOrderView.vue:292` | 用了 `Location` 但未 import | 含"水上/漂流"等名称时列表崩溃 |
| `RechargeApplicationView.vue:156` | 用了 `CloseCircle`（不存在，应为 `Close`） | 图标失效 |
| `AIChatView.vue:53` | 用了 `Bot`（不存在） | AI 头像空白 |

**修复**：逐一改为实际存在的导出。已通过构建产物验证：新 bundle 中
`CheckCircle`/`CloseCircle`/`Bot` **均已消失**，`CircleCheck` 存在。

### P1 级修复

- **`RefreshTokenStore` fail-open** → 改为 **fail-closed**：Redis 不可用时拒绝刷新，
  而非放行。原实现使"登出/改密吊销"在 Redis 故障期间完全失效。
- **refresh token 不轮换** → 新增 `consume()`，每次刷新作废旧 token 并签发新的。
  重放旧 token 会吊销该用户全部 refresh token。**实测：重放返回 401。**
- **JWT 唯一性** → 为 access/refresh token 添加 `jti`。`iat`/`exp` 只有秒级精度，
  同一秒内为同一用户连续签发会得到**完全相同的字符串**，这会同时破坏轮换与登出黑名单。
  （此问题在轮换实测中被发现并修复。）
- **XSS** → `AIChatView.formatText` 的转义**完全是空操作**
  （`.replace(/&/g,'&')` 等恒等替换），经 `v-html` 渲染 → 改为正确的 HTML 实体转义。
- **`permitAll` 过宽** → `/v1/project/**` 收窄为仅 `GET /v1/project/list` 与
  `GET /v1/project/*`，避免未来新增的写接口默认公开。
- **登出 TTL** → 改用 token 真实剩余时间，而非硬编码 1440。
- **`canView` fail-closed** → 未知角色不再因 `0 >= 0` 被放行。

### P2 级清理

- **新增 ESLint 静态检查**（`myvue/eslint.config.mjs`）——针对本项目反复出现的
  "引用了未定义符号导致整页白屏"问题。**`vite build` 不会报错，只在运行时渲染才炸**，
  ESLint 的 `no-undef` 正好补上这个缺口。
  **已用"重新注入原 bug"的方式验证其有效性**：把 `CircleCheck` 改回 `CheckCircle` 后，
  ESLint 精确报出 `'CheckCircle' is not defined`（行号也准确）。
  ```bash
  npm run lint         # 全量
  npm run lint:errors  # 只看 error
  ```
  > 注意：Element Plus 组件与图标在 `main.js` 里全局注册，因此 `el-*` 与图标名
  > 已加入 allowlist。否则会产生 400+ 条噪声，淹没真正的信号。

- **ESLint 发现的真实 bug**：
  - `ProjectView.vue` / `UserView.vue` 的 `<el-radio :label="1" label="...">`
    **重复传了 `label`**。EP 2.x 中绑定值应为 `value`、`label` 是显示文案，
    导致单选框的值变成文案字符串而非 1/0，**状态开关无法正确回填**。已改为 `:value`。
  - `request.js` 中 `let message = '网络异常...'` 在读取前就被无条件覆盖，
    该默认值永远不会生效。
- 清除 31 处未使用的 import / 变量（含 `handleSearch` 这类与既有函数重复的死代码）
- 删除两组重复测试文件（根包 vs 子包），保留子包版本
- 删除死代码：`ScenicProjectMapperStruct`（无人注入）、`JwtUtil.generateTokenFromRefresh`
- 删除垃圾文件 `D ai项目ai景区管理 start.bat/.sh`（文件名含路径的复制粘贴事故，
  `.sh` 与 `start.sh` 的 MD5 完全相同）
- 新增 `myvue/src/utils/format.js`：`formatMoney`/`firstLetter`/`formatDateTime`，
  修复 `parseFloat(x).toFixed(2)` 在 null 时显示 `¥NaN`、
  `username.charAt(0)` 在 null 时抛 TypeError 导致整块渲染失败
- `stores/user.js`：`JSON.parse` 加保护 —— 它运行在 store 初始化阶段，
  数据损坏会导致**整个应用启动失败（白屏且无法恢复）**
- 修复搜索按钮未重置 `current=1`（从第 3 页搜索返回空结果）
- 新增 `t_user.role` 索引；补 `micrometer-registry-prometheus` 依赖
- **补充缺失的 `spring-boot-starter-actuator` 依赖**：`SecurityConfig` 放行了
  `/actuator/health`、配置也写了，但依赖从未声明 → 一直返回 500。
  修复后返回 `{"status":"UP"}`。**（`CLAUDE.md` 此前声称"/actuator/health 返回 HTTP 200"是错的。）**

### 验证结果

- **测试：59 个全部通过**（新增 `OrderVisibilityTest`、`RefundApprovalFlowTest`、
  `RefreshTokenStoreTest`、`JwtUtilTest`）
- **ESLint：0 error / 0 warning**；并已验证它确实能捕获原有的白屏 bug
- **端到端实测**（真实启动应用 + 数据库核对）：
  - health `UP`
  - 10 条订单可见（修复前为 0）
  - **退款完整往返**：库存 9997 →（购票 3 张）9994 →（审批退款）**9997**；
    余额 500.00 → 140.00 → **500.00**；订单 PAID → REFUNDED
  - 越权退款被拒（`400 无权操作此订单`）—— 管理员不能替他人申请退款
  - 重复审批拒绝，且钱与库存不再变动
  - token 轮换生效，重放旧 refresh token 返回 401
  - 三种提权攻击（自提/创建超管/非法角色值）全部 400 拒绝
  - 未认证访问受保护接口返回 401
- 前端构建通过，bundle 中不存在未定义图标符号

### Git 提交历史

```
589e9c6 fix(P0): remove global logic-delete config, fix token refresh path, fix 4 broken icons
8ad4492 feat(P0): refund approval workflow, single config file (Redis), cache+privilege fixes
d26adba fix(P1): security hardening - fail-closed token store, refresh rotation, XSS, CORS scope
b3afb33 chore(P2): remove dead code and duplicates, harden frontend rendering
e45961d fix(P0): MapStruct interfaces were being scanned as MyBatis mappers
07448eb chore: initial snapshot before audit/optimization   ← 基线，可随时回滚
```

### ⚠️ 待处理的安全事项

- `.env` 中的 `NVIDIA_API_KEY` 曾在对话中暴露，**建议吊销并重新生成**
- `application.yml` 原本硬编码了同一把 key（该文件已删除）

---

## Key Improvements (2026-10-13) — 前端视觉全面美化（第五轮）

> 本轮的方法是**先看再改**：用浏览器逐页截图，对着真实渲染结果找问题，
> 每处修改都在浏览器里量过尺寸、改完再复验。共修复 4 类系统性缺陷。

### 方法论要点

**列宽、图标这类问题靠读代码发现不了，必须实测。** 例如"数量"表头显示成
"数..."，静态看 CSS 完全正常（`width="80"`、`font-size: 14px`）。实测才发现
`.cell` 的 `padding: 16px` 吃掉了 32px，只剩 48px 可用；再叠加 Element Plus 给
排序图标预留的空间就放不下两个汉字。**改用 `getBoundingClientRect` +
`scrollWidth` 逐单元格比对，才能定位到真正的约束。**

同理，SVG 图标"撑满容器"的问题，只有截图才看得见——代码里写的是
`font-size: 44px`，看起来完全合理。

### 1. 表格文字折行（6 个列表页全部受影响，最严重）

**症状**：订单号显示成 `#0000` / `0037` 两行；手机号 `1380013800` / `1`；
金额 `¥360.` / `00`；时间折成两行。行高被撑到 ~124px，一屏只看得到 5 行。

**根因**：Element Plus 的 `.cell` 默认 `white-space: normal`，而列宽是写死的。
单元格左右各 16px 内边距后，"数量"列（80px）只剩 48px。

**修复**：
- `td .cell` 统一 `nowrap` + `ellipsis`（需多行的显式加 `.cell-wrap`）
- 单元格水平内边距 16px → 12px（与表头一致）
- 操作列改 `display: flex; flex-wrap: nowrap`（否则"删除"被挤到第二行）
- 按实测所需宽度逐列放宽

**效果**：行高 124px → 53px，一屏 5 行 → 7+ 行。

### 2. SVG 图标失去尺寸约束 → 巨型图标

⚠️ **Element Plus 的图标是 SVG，`font-size` 对它完全无效**，必须写
`width`/`height`。项目里大量样式只写了 `font-size`，容器一变成 flex/grid，
SVG 就撑满可用宽度：

| 位置 | 表现 |
|---|---|
| AI 客服页 | 巨型对勾盖住页面标题 |
| 退款审核页 | 空状态图标占满整屏 |
| 个人中心卡片标题 | 图标占满整行，把"个人信息"挤成竖排（每行一个字） |
| 个人中心操作按钮 | 图标吃掉整格，按钮显示为空白 |

**修复**：补齐 `width`/`height`；设计系统新增全局兜底
`svg:not([width]):not([height]) { max-width/height: 48px }`。

### 3. 个人中心操作按钮"白字白底"

`.action-btn` 强制白底，`type="primary"` 又让 Element Plus 输出白色文字 →
白字白底，按钮看起来完全是空的。本该着色的 `.action-btn--*` 修饰类
因模板写的是 `type=` 而从未生效。

**修复**：改用修饰类；`.action-btn` 显式声明基础文字色（防止再次白字白底）。

> 另注：EP 会把按钮内容包进一个 `<span style="display:flex">`，
> 只设 `.el-button` 的 `flex-direction` 不起作用，必须 `:deep(> span)`。

### 4. 生产构建原本是失败的（本轮最重要的发现之一）

`design-system.css` 的注释里写了 `Recharge*/Vip*`。其中的 **`*/` 提前终止了
注释**，后面的文字变成非法 CSS。

- 浏览器宽容 → 开发期完全无感
- `lightningcss` 压缩时报 `Unexpected token Delim('*')` → **`npm run build` 直接失败**
- **ESLint 不检查 CSS，所以 lint 是 0 error，问题被完全掩盖**

**教训**：改完前端**必须跑 `npm run build`**，不能只跑 lint。
注释里避免出现 `*/`（写"Recharge 系列 / Vip 系列"而不是 `Recharge*/Vip*`）。

### 5. 其他修复

- **引入 Element Plus 中文语言包**（`main.js`）：此前分页显示
  "Total 1 / 10/page / Go to"。现已为"共 N 条 / 10条/页 / 前往 / 页"。
- **VIP 申请页"温馨提示"**把数组直接绑给 `:description`，
  页面显示成原始 JSON `["...","..."]`；改为默认插槽 + 列表渲染。
- **面包屑**在 `/projects` 下返回空数组，顶栏只剩"首页"、与页面标题重复；
  改为所有路由都显示当前页名（首页 / 景点项目）。
- **AI 客服页** `max-width` 900px → 1280px；聊天卡片高度改用 `100dvh` 计算，
  修复"输入框被顶到屏幕外、必须滚动才能打字"。
- **项目封面**无图占位改为渐变 + 遮罩网格纹理，不再像加载失败。

### 验证结果

- `npm run lint` → **0 error / 0 warning**
- `npm run build` → **成功**（修复前会失败）
- 以 **admin** 与 **testuser** 两种角色逐页截图复验
- 6 个列表页的单元格溢出检测（`scrollWidth > clientWidth`）**全部归零**

### Git 提交历史

```
d7eb66a fix(ui): repair table/filter/icon defects found by visual QA across all pages
cbbaeab feat(ui): redesign the design system, login page and app shell
```

## Key Improvements (2026-10-13 续) — 把应用真正跑起来后发现的 P0

> 上一轮只做了前端美化。这一轮的"再检查一下"是把应用**真正点一遍**：
> 登录、点每一个菜单、开对话框、搜筛选、发 AI 提问、买一张票。
> 结果发现的问题，**静态审阅和单元测试都发现不了**。

### 1. 🔴 AI 客服永久不可用（P0）——同一个 bug 的第二条序列化路径

真实点击"门票多少钱？"，界面返回"系统繁忙，请稍后再试"。后端日志：

```
java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to
  class com.example.mpdemo.entity.KnowledgeEntry
  at AIChatService.retrieve(AIChatService.java:180)
```

**根因**：第四轮修 Redis 序列化时，**只修了 CacheManager，漏了 RedisTemplate**：

| Bean | 序列化器 | 结果 |
|---|---|---|
| `cacheManager` | `typedObjectMapper()`（含 `@class`） | ✅ 正常 |
| `redisTemplate` | `safeObjectMapper()`（关闭 DefaultTyping） | ❌ 丢类型 |

`AIChatService` 用 `redisTemplate.opsForValue()` 直接读写知识库缓存，
写出的 JSON 不含 `@class`，读回来每个元素都是 `LinkedHashMap`。

**为什么极难定位**：`(List<KnowledgeEntry>) cached` 是**泛型强转，运行时类型被擦除**，
在这一行**根本不报错**；直到元素被当作 `KnowledgeEntry` 使用
（`retrieve()` → `calculateSimilarity`）才抛异常。
**堆栈完全指向检索逻辑，看不出和序列化有关。**

**修复**：
1. `redisTemplate` 改用 `typedObjectMapper()`，与 `cacheManager` 一致
   （仍是白名单 DefaultTyping，不引入反序列化攻击面）。
2. `loadKnowledge()` 增加**防御性类型校验**：读缓存后检查元素是否真是
   `KnowledgeEntry`，不符则记日志 + 清缓存 + 回退数据库直查。
   这样同类问题下次会以清晰的告警出现，而不是一个看似无关的 ClassCastException。
3. `safeObjectMapper()` 标记 `@Deprecated` 并写明"禁止用于 Redis 值序列化"。

**必须清旧缓存**：修复后旧条目仍不含 `@class`，在 TTL 到期前会继续失败。
```bash
redis-cli del ai:knowledge:full
```
实测修复后写入的值为：
```json
["java.util.ArrayList",[{"@class":"com.example.mpdemo.entity.KnowledgeEntry",...
```

> **教训**：本项目有**两条 Redis 序列化路径**。改序列化配置时
> **两个 Bean 必须一起改**，只改一个会留下更难查的隐性故障。

### 2. AI 请求必然超时（前端 10s < 实际 15-25s）

`request.js` 全局 `timeout: 10000`，但 AI 接口要调用外部大模型（NVIDIA），
**实测单次往返 15-25s**。表现为 AI 客服"时好时坏"，
报 `timeout of 10000ms exceeded`，极易被误判成后端故障。

后端 `AIChatService` 的 HttpClient 只设了 `connectTimeout(15s)`、
**没设 readTimeout**，服务端会一直等——所以问题完全在客户端。

**修复**：`chatAI()` 单独设 `timeout: 90000`。

### 3. AI 回复把 Markdown 原样显示

模型返回 `**成人票**：120元/人`，而 `formatText` 只做 HTML 转义 + 换行，
页面上直接显示一堆 `**`。

**修复**：在**已完成实体转义**的前提下（输入里的 `<` `>` 已是 `&lt;` `&gt;`，
无法构造新标签），再做一层白名单转换：加粗 / 行内代码 / 标题 / 有序无序列表。

> 同时去掉 `.msg-text` 的 `white-space: pre-wrap`——
> 换行已由 `<br>` 表达，两者叠加会导致双倍行距。

### 4. 我的订单按钮左侧多一条竖线

`order-actions` 第一个子元素是孤立的 `<el-divider direction="vertical" />`，
前面没有内容，渲染出来就是按钮左边一个突兀的 `|`。已移除。

### 验证（真实运行）

- AI 问答**完整跑通**：提问 → 检索知识库 → 返回带格式答案
  （确认 Markdown 渲染为 5 个 `<strong>` + 5 个列表项，无残留 `**`）
- 购票往返：图书馆库存 10 → 9，订单 `#00000044` 出现在"我的订单"，
  统计卡片同步（1 全部订单 / 1 进行中 / ¥5.00 总消费）
- 搜索/重置筛选正常（搜 user1 → 1 条，重置 → 9 条）
- 11 个页面布局检测归零；后端 **61 个测试全部通过**；前端 lint 0 error / build 成功

---

## Key Improvements (2026-10-13 三次续) — 用户反馈"脚本启动不了"

### 症状与真相

用户报告 `start.bat` 启动不了。实测复现后发现：**脚本其实跑完了全部 6 个步骤**，
但后端从未真正就绪，而且它给出的提示是**误导性的**：

```
[5/6] Starting backend [port 9090]...
  Waiting for backend...
  [WARN] Backend not ready within 120s - check the Scenic-Backend window
```

### 根因：timeout.exe 在非交互环境下立即失败

等待循环用的是 `timeout /t 2 /nobreak`（60 次 ≈ 120 秒）。
`timeout.exe` 需要控制台输入句柄，没有控制台时会立刻报错退出：

```
ERROR: Input redirection is not supported, exiting the process immediately.
```

**于是"等待 120 秒"的循环在 1.1 秒内烧完了全部 60 次迭代**，
紧接着打印"Backend not ready within 120s"——
而此时 Spring Boot 才启动几秒钟，根本来不及绑定 9090。

| | 循环耗时 |
|---|---|
| 期望 | 120 秒 |
| 实际（修复前） | **1.11 秒** |
| 实际（修复后） | 10.4 秒 / 5 次（≈2s 每次）✅ |

**提示说"等了 120 秒"，实际一秒都没等** —— 这就是"启动不了"的真相；
后端只是被判定得太早了。

### 修复

- 延时改用 `ping -n 3 127.0.0.1 >nul`（有无控制台都可靠）
- 每 10 轮（约 20s）回报一次进度，便于区分"慢"与"卡住"
- 失败提示不再笼统，改为指向 Scenic-Backend 窗口并列出最常见原因
  （MySQL/Redis 未启动、DB_PASSWORD 不对）

### 验证（完整往返）

```
[1/6] Loading config...         OK
[2/6] Checking environment...   Java 21 / Node 24
[3/6] Checking services...      MySQL OK / Redis OK
[4/6] Checking frontend deps... OK
[5/6] Starting backend...       Backend ready: http://localhost:9090  ← 真实等待后成功
[6/6] Starting frontend...      Startup complete
```
- 后端 `/actuator/health` → `{"status":"UP"}`；前端 → HTTP 200
- `stop.bat` 正确定位并结束两个 PID，端口全部释放

### ⚠️ 本轮踩到的坑

修改 `.bat` 时**一度写入了中文注释**，产生 69 个非 ASCII 字节——
正是本项目第 1 条脚本约束所禁止的。已改回纯 ASCII，
并用字节级校验确认（非 ASCII 0 / 裸 LF 0）。
**改完 `.bat` 一定要校验编码。**

---

## Startup

### 一键脚本（推荐，2026-10-12 重写）

| 脚本 | 用途 |
|---|---|
| `start.bat` | 一键启动：读配置 → 检查环境 → 检查 MySQL/Redis → 装前端依赖 → 起后端 → **等 9090 就绪** → 起前端 |
| `stop.bat` | 停止服务：按端口精确定位并结束 9090 / 5173 |
| `run-backend.bat` | 只启动后端（可单独运行，便于排查） |
| `run-frontend.bat` | 只启动前端 |
| `env.bat` | `call env.bat` 把 `.env` 加载进当前 cmd 会话 |
| `clean.bat` | 交互式清理 `node_modules` / `target` / `dist` |
| `start.sh` | Linux / macOS 版一键启动 |

**为什么当初起不来（已修）**：旧 `start.bat` 硬编码 `DB_PASSWORD=root`（实际是 `123456`）、
**根本没启动前端**、盲等 15 秒不检查是否成功、Redis 变量为空时把空串传给 Spring
导致 `NumberFormatException`。配置现统一从 `.env` 读取——旧脚本把密码复制了一份到
脚本里，正是这样才和 `.env` 失去同步。

### ⚠️ 修改脚本时必须遵守的四条

1. **`.bat` 必须是纯 ASCII + CRLF。**
   `cmd.exe` 用 OEM 代码页解析 `.bat`，UTF-8 中文（**连 `REM` 注释里的也算**）
   会被错误解析并"溢出"成待执行命令，报
   `'xxx' is not recognized as an internal or external command`。
   所以本项目的 `.bat` 一律只用英文输出，中文放在 `.md` 文档里。
   `.gitattributes` 已把 `*.bat` 固定为 `crlf`、`*.sh` 固定为 `lf`。
   > 编辑后自查（两条都必须为 0）：
   > ```powershell
   > $b=[IO.File]::ReadAllBytes("start.bat")
   > ($b|?{$_ -gt 127}).Count          # 非 ASCII 字节
   > ```
   > **注意**：用编辑器/AI 工具改 `.bat` 很容易写入 UTF-8 中文，
   > 本项目已经踩过这个坑（改完产生 69 个非 ASCII 字节）。

2. **不要给 Spring 传空字符串的配置。**
   空值会让 `${REDIS_PORT:6379}` 拿到 `""` 而不是默认值。
   正确写法是先判断再赋值：`if not defined REDIS_PORT set "REDIS_PORT=6379"`。

3. **不要用 `powershell Test-NetConnection` 做端口探测。**
   它的引号与管道在 `for /f (...)` 括号块里会被 cmd 破坏。
   用 `netstat -an | findstr LISTENING`（见 `start.bat` 的 `:CHECK_PORT`）。

4. **不要在 `.bat` 里用 `timeout /t` 做延时。**
   `timeout.exe` 需要控制台输入句柄，在非交互环境（CI、IDE 任务、
   管道、部分终端）下会**立即失败**：
   ```
   ERROR: Input redirection is not supported, exiting the process immediately.
   ```
   如果它被放在等待循环里，循环会瞬间跑完 —— `start.bat` 曾因此
   **"等待 120 秒"实际只花 1.1 秒**，然后打印
   `[WARN] Backend not ready within 120s`，
   而此时后端才刚启动几秒。用户看到的就是"脚本启动不了"。
   **改用 `ping -n 3 127.0.0.1 >nul`**，它在有无控制台时都可靠。

### 手动启动

- **Docker (recommended):** `docker-compose up -d`
- **Backend:** `mvnw.cmd spring-boot:run` (requires MySQL + Redis)
  - Redis on Windows: `"C:\Program Files\Redis\redis-server.exe" --service-start`
  - 首次添加新依赖后需联网构建一次：`mvnw.cmd package`（不带 `-o`）
- **Frontend:** `npm run dev` (in myvue/)
- **DB Init:** `mysql -u root -p < sql/schema.sql`
- **增量迁移:** 依次执行 `sql/V2__add_indexes.sql`、`V3__add_soft_delete_and_indexes.sql`、
  `sql/V4__refund_application.sql`
- **测试:** `mvnw.cmd test`（需 MySQL + Redis 运行中）
- **前端 lint:** `npm run lint`（在 myvue/ 下）

## Environment Status (2026-10-12, 更新)
- **MySQL:** Running on localhost:3306（数据库 `scenic_db`）
- **Redis:** Windows 服务 `Redis` 已安装并运行；`redis-cli ping` 返回 `PONG`
- **Cache:** **`spring.cache.type=redis`**（唯一配置源为 `application.properties`；
  `application.yml` 已删除，两者共存会导致属性合并不确定）
- **Backend:** `mvnw.cmd spring-boot:run`，端口 9090；`/actuator/health` 返回
  `{"status":"UP"}`（需 `spring-boot-starter-actuator` 依赖，本轮已补上）
- **Frontend:** Vite dev server 端口 5173
- **测试:** `mvnw.cmd test` → **59 个测试全部通过**
- **Docker:** 未安装（可选）

## Build Status (2026-10-12)
- **打包:** `mvnw.cmd package -DskipTests` 成功
- **测试:** 59 pass / 0 fail / 1 skipped
- **前端 lint:** `npm run lint`（在 myvue/）→ 0 error / 0 warning
- **前端构建:** `npm run build` 成功
- **注意:** 首次添加新依赖需要联网（`mvnw.cmd package`，不带 `-o`）；
  依赖齐备后可用 `-o` 离线构建。前端依赖含 `--legacy-peer-deps` 安装的
  ESLint 相关包（仅开发期使用，不进产物）

## 本轮新增的架构约束（务必遵守）

1. **MapStruct 接口禁止放在 `com.example.mpdemo.mapper` 包**。
   `@MapperScan("com.example.mpdemo.mapper")` 会把它当作 MyBatis mapper 注册，
   覆盖 MapStruct 生成的实现，导致 `BindingException`。
   **MapStruct 一律放 `com.example.mpdemo.converter`。**

2. **禁止配置全局 `mybatis-plus.global-config.db-config.logic-delete-field`**。
   本项目的 `status` 是业务状态字段（订单存 `PAID`/`CANCELLED` 等），
   不是删除标记。需要软删除时用 `@TableLogic` 按实体标注。

3. **只保留 `application.properties` 一个配置文件**。
   与 `application.yml` 共存会导致属性优先级不确定、行为难以推理。

4. **退款必须走审批流**（`t_refund_application`），不得直接改订单状态退款。
   审批通过时才退余额 + 恢复库存。

5. **新增/修改缓存读取时，务必确认失效路径能覆盖到它**。
   项目缓存统一用 `cache.clear()` 整体清空，避免 key 拼写不一致导致陈旧数据。

6. **改动前端后必须跑 `npm run lint`**。
   本项目多次出现"模板引用了不存在的符号"，`vite build` 不报错但运行时会整页白屏。
   ESLint 的 `no-undef` 是唯一能自动拦住这类问题的关卡。

7. **不要把「关闭 DefaultTyping 的 ObjectMapper」用于 Redis 缓存值序列化。**
   `GenericJackson2JsonRedisSerializer` 依赖 JSON 里的 `@class` 还原类型。
   若 ObjectMapper 关闭了 DefaultTyping，写入不含 `@class`，读取只能得到
   `LinkedHashMap` → Spring 把它当方法返回类型返回 → `ClassCastException` → 500。
   **典型症状：同一接口第 1 次 200（未命中缓存），第 2 次起 500（命中缓存）。**
   正确做法见 `RedisConfig.typedObjectMapper()`：用
   `BasicPolymorphicTypeValidator` 白名单开启 DefaultTyping，兼顾类型还原与安全。
   `CacheRoundTripTest` 已覆盖此场景。

   ⚠️ **本项目的 Redis 序列化有两条路径，改动时必须一起改：**
   `cacheManager`（给 `@Cacheable` 用）与 `redisTemplate`（编程式读写，
   如 `AIChatService` 的知识库缓存）。第四轮只改了前者，
   导致 AI 客服永久不可用，且异常堆栈指向检索逻辑、与序列化毫无关联，
   **直到把应用跑起来点"门票多少钱"才暴露**。

   **排查提示**：`(List<X>) cached` 这类泛型强转**运行时会被擦除、不会报错**，
   真正的异常发生在元素被当 `X` 使用的地方。怀疑此类问题时，
   直接查 Redis 原值是否含 `@class`：
   ```bash
   redis-cli get ai:knowledge:full    # 若开头是 [{"id":... 而非 ["java.util.ArrayList",[{"@class":...
   ```

8. **改动前端后必须跑 `npm run build`，不能只跑 `npm run lint`。**
   ESLint **不检查 CSS**。本项目就出现过注释里写 `Recharge*/Vip*`、
   其中的 `*/` 提前终止注释 → 后续文字变成非法 CSS → lint 全绿但
   `vite build`（lightningcss）直接失败的情况。
   **两者都要跑：`lint` 管 JS/模板符号，`build` 管 CSS 语法与打包。**

   ⚠️ 反过来，**前端问题也不能只靠 lint/build 判断**：
   AI 接口的 10s 超时（实际需 15-25s）、Markdown 原样显示，
   都是构建全绿但功能实际不可用，**必须真正跑起来点一遍**。

9. **Element Plus 图标是 SVG，`font-size` 对它无效，必须写 `width`/`height`。**
   只写 `font-size` 的图标一旦处于 flex/grid 容器中就会撑满可用宽度，
   表现为"巨型图标"（盖住标题、占满整屏、把标题挤成竖排）。
   设计系统已有全局兜底 `svg:not([width]):not([height]) { max-width/height: 48px }`，
   但新写的图标样式仍应显式给出尺寸。

10. **表格单元格默认禁止折行**（设计系统里 `td .cell { white-space: nowrap }`）。
    新增列时若内容会被截断，请**放宽该列 width**，而不是取消 nowrap；
    确需多行显示的列显式加 `.cell-wrap` 类。
    列宽参考实测值：订单号/申请单号 ≥132、ID ≥96、手机号 ≥152、
    完整时间 ≥200、IP 地址 ≥176。
    改动表格后建议用
    `scrollWidth > clientWidth` 逐单元格自查是否溢出。


## Env Variables Required
- `DB_PASSWORD` — MySQL root password
- `JWT_SECRET` — JWT signing key (min 32 chars, enforced at startup)
- `DASHSCOPE_API_KEY` — Alibaba DashScope API key
- `REDIS_HOST` — Redis hostname (default: localhost)
- `REDIS_PORT` — Redis port (default: 6379)

## Configurable Properties (application.properties — 唯一配置源)

```properties
# Swagger: set to true for dev, false for production
springdoc.api-docs.enabled=false

# Cache: Redis-backed (RedisConfig 定义 CacheManager 与各缓存 TTL)
spring.cache.type=redis

# Business config
scenic.vip-discount=0.8
scenic.max-ticket-quantity=10
scenic.login-max-attempts=5
scenic.login-lock-minutes=10
scenic.register-rate-limit=5
scenic.login-rate-limit=20
scenic.ai-chat-rate-limit=10
scenic.order-timeout-minutes=30
scenic.vip-valid-days=30
scenic.recharge-min-amount=1.00
scenic.recharge-max-amount=10000.00
```

## 退款审批 API（2026-10-12 新增）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/v1/order/{id}/refund` | 已登录 | 提交退款申请，订单 → `REFUND_PENDING` |
| GET | `/v1/order/myRefunds` | 已登录 | 查询自己的退款申请 |
| GET | `/v1/order/pendingRefunds` | ADMIN/SUPER_ADMIN | 查询待审批申请 |
| POST | `/v1/order/approveRefund` | ADMIN/SUPER_ADMIN | 审批；通过则退款 + 恢复库存 |

订单状态流转：
```
PAID/COMPLETED --申请--> REFUND_PENDING --通过--> REFUNDED
                                       --拒绝--> PAID
```

