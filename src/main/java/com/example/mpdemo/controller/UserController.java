package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.config.IdempotencyService;
import com.example.mpdemo.config.RateLimiter;
import com.example.mpdemo.config.RefreshTokenStore;
import com.example.mpdemo.config.TokenBlacklist;
import com.example.mpdemo.dto.ChangePasswordDTO;
import com.example.mpdemo.dto.CreateUserDTO;
import com.example.mpdemo.dto.UpdateUserDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.converter.UserConverter;
import com.example.mpdemo.mapper.UserMapper;
import com.example.mpdemo.service.LoginAttemptService;
import com.example.mpdemo.service.OperationLogService;
import com.example.mpdemo.util.JwtUtil;
import com.example.mpdemo.util.RoleLevelUtil;
import com.example.mpdemo.vo.LoginVO;
import com.example.mpdemo.vo.UserListVO;
import com.example.mpdemo.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/user")
@Tag(name = "用户管理", description = "用户登录、注册、信息管理接口")
public class UserController extends BaseController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserConverter userConverter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private RoleLevelUtil roleLevelUtil;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @GetMapping("/findAll")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "获取用户列表（管理员）")
    public Result findAll(@RequestParam(required = false) String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }

        List<User> allUsers = userMapper.selectList(queryWrapper);

        List<UserListVO> voList = allUsers.stream()
                .filter(user -> roleLevelUtil.canView(getCurrentRole(), user.getRole()))
                .map(userConverter::toListVO)
                .collect(Collectors.toList());

        return Result.success("获取成功", voList);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "获取用户详情（管理员）")
    public Result findById(@PathVariable Integer id) {
        User targetUser = userMapper.selectById(id);
        if (targetUser == null) {
            return Result.error("用户不存在");
        }

        String currentRole = getCurrentRole();
        if (!roleLevelUtil.canView(currentRole, targetUser.getRole())) {
            return Result.error("权限不足，无法查看该用户信息");
        }

        return Result.success("获取成功", userConverter.toVO(targetUser));
    }

    @GetMapping("/findByPage")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "分页获取用户列表（管理员）")
    public Result findByPage(@RequestParam(defaultValue = "1") Integer current,
                             @RequestParam(defaultValue = "10") Integer size,
                             @RequestParam(required = false) String username) {
        Page<User> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }

        String currentRole = getCurrentRole();
        if ("ROLE_ADMIN".equals(currentRole)) {
            queryWrapper.in("role", "USER", "VIP", "ADMIN");
        }

        queryWrapper.orderByDesc("create_time");

        IPage<User> result = userMapper.selectPage(page, queryWrapper);
        IPage<UserVO> voPage = result.convert(userConverter::toVO);

        return Result.success("获取成功", voPage);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "创建用户（管理员）")
    public Result save(@RequestBody @Valid CreateUserDTO dto, HttpServletRequest request) {
        // 角色必须合法且不得高于当前操作者（防止 ADMIN 创建 SUPER_ADMIN）
        String newRole = dto.getRole() != null ? dto.getRole() : "USER";
        if (!roleLevelUtil.isValidRole(newRole)) {
            return Result.error("非法的角色值");
        }
        if (!roleLevelUtil.canManageRole(getCurrentRole(), newRole)) {
            return Result.error("权限不足，无法创建该角色的用户");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        if (userMapper.selectCount(queryWrapper) > 0) {
            return Result.error("用户名已存在");
        }

        User user = userConverter.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        int rows = userMapper.insert(user);
        if (rows > 0) {
            logOperation(user.getId(), user.getUsername(), "用户管理", "创建",
                    "创建用户: " + user.getUsername(), "POST /v1/user", null, "success",
                    getClientIp(request), null);
            return Result.success("添加成功", null);
        }
        return Result.error("添加失败");
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "更新用户（管理员）")
    public Result update(@RequestBody @Valid UpdateUserDTO dto, HttpServletRequest request) {
        User existingUser = userMapper.selectById(dto.getId());
        if (existingUser == null) {
            return Result.error("用户不存在");
        }

        String currentRole = getCurrentRole();
        if (!roleLevelUtil.canView(currentRole, existingUser.getRole())) {
            return Result.error("权限不足，无法修改该用户信息");
        }

        // 角色变更必须单独授权，否则任何 ADMIN 都能把自己/他人提升为 SUPER_ADMIN。
        // （updateEntity 会原样写入 dto.role，所以此校验不能省略。）
        if (dto.getRole() != null && !dto.getRole().equals(existingUser.getRole())) {
            if (!roleLevelUtil.isValidRole(dto.getRole())) {
                return Result.error("非法的角色值");
            }
            // 不能把任何人提升为高于自己的等级（含自己）
            if (!roleLevelUtil.canManageRole(currentRole, dto.getRole())) {
                return Result.error("权限不足，无法授予该角色");
            }
            // 不能修改等级高于自己的用户（防止降级他人后再接管）
            if (!roleLevelUtil.canManageRole(currentRole, existingUser.getRole())) {
                return Result.error("权限不足，无法修改该用户的角色");
            }
        }

        String oldUsername = existingUser.getUsername();
        String oldRole = existingUser.getRole();

        userConverter.updateEntity(dto, existingUser);

        int rows = userMapper.updateById(existingUser);
        if (rows > 0) {
            String detail = "更新用户 " + oldUsername + " -> " + existingUser.getUsername();
            if (!oldRole.equals(existingUser.getRole())) {
                detail += "（角色 " + oldRole + " -> " + existingUser.getRole() + "）";
            }
            logOperation(existingUser.getId(), existingUser.getUsername(), "用户管理", "更新",
                    detail, "PUT /v1/user", null, "success", getClientIp(request), null);
            return Result.success("更新成功", null);
        }
        return Result.error("更新失败");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "删除用户（管理员）")
    public Result delete(@PathVariable Integer id, HttpServletRequest request) {
        User targetUser = userMapper.selectById(id);
        if (targetUser == null) {
            return Result.error("用户不存在");
        }

        String currentRole = getCurrentRole();
        if (!roleLevelUtil.canView(currentRole, targetUser.getRole())) {
            return Result.error("权限不足，无法删除该用户");
        }

        if ("SUPER_ADMIN".equals(targetUser.getRole()) && !roleLevelUtil.isSuperAdmin(currentRole)) {
            return Result.error("权限不足，仅高级管理员可删除高级管理员");
        }

        int rows = userMapper.deleteById(id);
        if (rows > 0) {
            logOperation(targetUser.getId(), targetUser.getUsername(), "用户管理", "删除",
                    "删除用户: " + targetUser.getUsername(), "DELETE /v1/user/" + id, null, "success",
                    getClientIp(request), null);
            return Result.success("删除成功", null);
        }
        return Result.error("删除失败");
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result register(@RequestBody com.example.mpdemo.entity.LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return Result.error("用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return Result.error("密码长度至少6位");
        }

        // 注册接口限流：同一 IP 每分钟最多 5 次注册
        String clientIp = getClientIp(request);
        if (!rateLimiter.tryAcquire("register:" + clientIp, 5, 60)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        // 幂等保护：防止重复提交注册
        String idempotencyKey = "register:" + clientIp + ":" + username;
        if (!idempotencyService.tryAcquire(idempotencyKey, 10)) {
            return Result.error("请勿重复提交注册请求");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        if (userMapper.selectCount(queryWrapper) > 0) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setStatus(1);
        user.setBalance(BigDecimal.ZERO);

        int rows = userMapper.insert(user);
        if (rows > 0) {
            return Result.success("注册成功", null);
        }
        return Result.error("注册失败");
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result login(@RequestBody com.example.mpdemo.entity.LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        if (username == null || username.isBlank()) {
            return Result.error("用户名不能为空");
        }
        String clientIp = getClientIp(request);

        // 接口限流：同一 IP 每分钟最多 20 次登录尝试
        if (!rateLimiter.tryAcquire("login:" + clientIp, 20, 60)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        if (loginAttemptService.isLocked(username)) {
            long remainingMinutes = loginAttemptService.getLockRemainingMinutes(username);
            return Result.error("账户已被锁定，请 " + remainingMinutes + " 分钟后再试");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            loginAttemptService.loginFailed(username);
            return Result.error("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.error("账户已被禁用，请联系管理员");
        }

        boolean passwordMatch = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());

        if (passwordMatch) {
            loginAttemptService.loginSucceeded(username);

            if (user.getBalance() == null) {
                user.setBalance(BigDecimal.ZERO);
            }

            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
            // 登记 refresh token，便于登出/改密时吊销
            refreshTokenStore.add(user.getId(), refreshToken);

            LoginVO loginVO = LoginVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .balance(user.getBalance())
                    .roleLevel(roleLevelUtil.getRoleLevel(user.getRole()))
                    .token(token)
                    .refreshToken(refreshToken)
                    .build();

            logOperation(user.getId(), user.getUsername(), "用户管理", "登录",
                    "用户登录", "POST /v1/user/login", null, "success", clientIp, null);

            return Result.success("登录成功", loginVO);
        } else {
            loginAttemptService.loginFailed(username);
            int remainingAttempts = loginAttemptService.getRemainingAttempts(username);

            if (remainingAttempts == 0) {
                return Result.error("密码错误次数过多，账户已被锁定 10 分钟");
            } else {
                return Result.error("用户名或密码错误");
            }
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public Result logout(HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details != null) {
            logOperation(details.getUserId(), details.getUsername(), "用户管理", "登出",
                    "用户登出", "POST /v1/user/logout", null, "success",
                    getClientIp(request), null);
        }

        // 将当前 token 加入黑名单，使其立即失效
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklist.addToBlacklist(token, 1440); // 1 天后自动清除
        }

        // 吊销该用户名下所有 refresh token，登出后旧 refresh token 无法再换取新 access token
        if (details != null) {
            refreshTokenStore.clear(details.getUserId());
        }

        return Result.success("退出成功", null);
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前用户信息")
    public Result getCurrentUser() {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("未登录");
        }

        User user = userMapper.selectById(details.getUserId());
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }

        return Result.success("获取成功", userConverter.toVO(user));
    }

    @PostMapping("/changePassword")
    @Operation(summary = "修改密码")
    public Result changePassword(@RequestBody ChangePasswordDTO params, HttpServletRequest request) {
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error("请先登录");
        }

        if (params.getOldPassword() == null || params.getNewPassword() == null) {
            return Result.error("参数不完整");
        }
        if (params.getNewPassword().length() < 6) {
            return Result.error("新密码长度至少6位");
        }

        User user = userMapper.selectById(details.getUserId());
        if (!passwordEncoder.matches(params.getOldPassword(), user.getPassword())) {
            return Result.error("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(params.getNewPassword()));
        int rows = userMapper.updateById(user);

        if (rows > 0) {
            // 密码修改后，将当前 token 加入黑名单（TTL = token 剩余有效时间，确保彻底失效）
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String currentToken = authHeader.substring(7);
                long remainingMinutes = jwtUtil.getRemainingMinutes(currentToken);
                tokenBlacklist.addToBlacklist(currentToken, remainingMinutes);
            }
            // 注意：不主动 invalidate 所有历史 token（用户可能在其他设备登录），
            // 而是让现有 token 自然过期（最长 24h），或通过登出操作主动清理
            // 但所有已签发的 refresh token 一律吊销，防止旧凭证继续换取新 access token
            refreshTokenStore.clear(details.getUserId());
            logOperation(user.getId(), user.getUsername(), "用户管理", "修改密码",
                    "用户修改密码", "POST /v1/user/changePassword", null, "success",
                    getClientIp(request), null);
            return Result.success("密码修改成功", null);
        }
        return Result.error("密码修改失败");
    }

    /**
     * 刷新 Token
     * <p>
     * 使用 refresh_token 换取新的 access_token + refresh_token。
     * <p>
     * <b>轮换（rotation）：</b>每次刷新都会作废旧的 refresh token 并签发新的，
     * 因此一个被盗的 refresh token 只能用一次，无法在 7 天有效期内反复使用。
     * 若旧 token 已被作废却再次出现（重放），说明凭证可能已泄露，
     * 此时吊销该用户全部 refresh token，强制重新登录。
     */
    @PostMapping("/refreshToken")
    @Operation(summary = "刷新 Token")
    public Result refreshToken(@RequestBody com.example.mpdemo.dto.RefreshTokenDTO dto) {
        if (dto.getRefreshToken() == null || dto.getRefreshToken().isBlank()) {
            return Result.error(401, "无效的刷新令牌");
        }
        try {
            if (!jwtUtil.validateRefreshToken(dto.getRefreshToken())) {
                return Result.error(401, "无效的刷新令牌");
            }

            Integer userId = jwtUtil.getUserId(dto.getRefreshToken());
            if (userId == null) {
                return Result.error(401, "无效的刷新令牌");
            }

            // 原子消费：作废旧 token。返回 false 表示该 token 不存在或已被使用
            boolean consumed = refreshTokenStore.consume(userId, dto.getRefreshToken());
            if (!consumed) {
                // 可能是重放攻击，或 Redis 不可用（fail-closed）。
                // 保守起见吊销该用户全部 refresh token，强制重新登录。
                refreshTokenStore.clear(userId);
                log.warn("refresh token 轮换失败（可能为重放或 Redis 不可用），已吊销用户 {} 的全部 refresh token",
                        userId);
                return Result.error(401, "刷新令牌已失效，请重新登录");
            }

            // 签发新的 access token 与 refresh token，并登记新 refresh token
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error(401, "用户不存在，请重新登录");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                return Result.error(401, "账户已被禁用，请联系管理员");
            }

            String newAccessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
            refreshTokenStore.add(userId, newRefreshToken);

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken);

            return Result.success("Token 刷新成功", data);
        } catch (Exception e) {
            log.warn("refresh token 处理异常", e);
            return Result.error(401, "无效的刷新令牌");
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 记录操作日志（实际调用异步 Service）
     */
    private void logOperation(Integer userId, String username, String module, String action,
                              String description, String requestMethod, String requestParams,
                              String responseResult, String ipAddress, Long duration) {
        operationLogService.log(userId, username, module, action, description,
                requestMethod, requestParams, responseResult, ipAddress, duration);
    }
}