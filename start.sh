#!/usr/bin/env bash
# ============================================================
# 景区管理系统 - 一键启动 (Linux / macOS)
# ============================================================
# 与 start.bat 行为保持一致：读 .env、检查依赖、同时拉起前后端。
#
# 修复的历史问题：
#   1. 旧脚本硬编码 DB_PASSWORD=root，但实际密码是 123456 → 后端起不来
#   2. 旧脚本用 `mvn spring-boot:run &` 后台启动但不检查是否成功，
#      失败时仍打印"启动完成"，误导使用者
#   3. 旧脚本 set -e 与 `&` 后台任务组合，前端启动失败不会暴露
#   4. NVIDIA_API_KEY 明文硬编码在脚本里（应改为从 .env 读取）
# ============================================================

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "============================================"
echo "   景区管理系统 - 一键启动"
echo "============================================"
echo "项目目录: $ROOT_DIR"
echo

# ---------- 1. 读取 .env ----------
echo "[1/6] 读取配置..."
if [[ ! -f "$ROOT_DIR/.env" ]]; then
    if [[ -f "$ROOT_DIR/.env.example" ]]; then
        cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
        echo "  未找到 .env，已从 .env.example 生成，请按需修改密码与密钥"
    else
        echo "  [错误] 缺少 .env 与 .env.example，无法确定数据库密码"
        exit 1
    fi
fi

# 仅导出形如 KEY=VALUE 的行，忽略注释与空行
set -a
# shellcheck disable=SC1090
source <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$ROOT_DIR/.env" | sed 's/\r$//')
set +a

: "${REDIS_HOST:=localhost}"
: "${REDIS_PORT:=6379}"
: "${REDIS_PASSWORD:=}"
export REDIS_HOST REDIS_PORT REDIS_PASSWORD

if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "  [错误] .env 中缺少 DB_PASSWORD"
    exit 1
fi
if [[ -z "${JWT_SECRET:-}" || ${#JWT_SECRET} -lt 32 ]]; then
    echo "  [错误] JWT_SECRET 未设置或长度不足 32 字符"
    exit 1
fi
if [[ -z "${NVIDIA_API_KEY:-}" ]]; then
    echo "  [警告] NVIDIA_API_KEY 未设置，AI 客服功能将不可用"
fi
echo "  已加载: DB_PASSWORD / JWT_SECRET / NVIDIA_API_KEY"
echo

# ---------- 2. 检查工具链 ----------
echo "[2/6] 检查运行环境..."
if ! command -v java >/dev/null 2>&1; then
    echo "  [错误] 未找到 java。请安装 JDK 17+"
    exit 1
fi
echo "  Java: $(java -version 2>&1 | head -n1)"

if ! command -v node >/dev/null 2>&1; then
    echo "  [错误] 未找到 node。请安装 Node.js 18+"
    exit 1
fi
echo "  Node: $(node -v)"

# 优先用 Maven Wrapper，避免依赖机器上是否装了 mvn
if [[ -x "$ROOT_DIR/mvnw" ]]; then
    MVN_CMD="$ROOT_DIR/mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN_CMD="mvn"
else
    echo "  [错误] 未找到 mvnw 或 mvn"
    exit 1
fi
echo "  Maven: $MVN_CMD"
echo

# ---------- 3. 检查依赖服务 ----------
echo "[3/6] 检查依赖服务..."

# 端口探测：优先 nc，其次 bash 内置 /dev/tcp
port_open() {
    local host="$1" port="$2"
    if command -v nc >/dev/null 2>&1; then
        nc -z "$host" "$port" >/dev/null 2>&1
    else
        (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1
    fi
}

if port_open localhost 3306; then
    echo "  MySQL  (3306): 已就绪"
else
    echo "  [错误] MySQL (3306) 未运行。请先启动 MySQL"
    exit 1
fi

if port_open localhost "$REDIS_PORT"; then
    echo "  Redis  ($REDIS_PORT): 已就绪"
else
    echo "  [警告] Redis ($REDIS_PORT) 未运行。缓存与限流将降级，"
    echo "         且刷新 token 会失败（需重新登录）。建议先启动 Redis。"
fi
echo

# ---------- 4. 前端依赖 ----------
echo "[4/6] 检查前端依赖..."
if [[ ! -d "$ROOT_DIR/myvue/node_modules" ]]; then
    echo "  首次运行，安装前端依赖（可能需要几分钟）..."
    if ! (cd "$ROOT_DIR/myvue" && npm install); then
        echo "  [错误] npm install 失败"
        exit 1
    fi
else
    echo "  前端依赖已存在，跳过 npm install"
fi
echo

# ---------- 清理钩子 ----------
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo
    echo "正在停止服务..."
    [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
    [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
    wait 2>/dev/null || true
    echo "已停止所有服务"
    exit 0
}
trap cleanup INT TERM

# ---------- 5. 启动后端 ----------
echo "[5/6] 启动后端 (端口 9090)..."
"$MVN_CMD" spring-boot:run > "$ROOT_DIR/target-backend.log" 2>&1 &
BACKEND_PID=$!
echo "  后端 PID: $BACKEND_PID，日志: target-backend.log"

# 轮询健康检查，而不是盲等固定秒数
echo "  等待后端就绪..."
BACKEND_UP=0
for _ in $(seq 1 60); do
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "  [错误] 后端进程已退出，请查看 target-backend.log"
        tail -n 30 "$ROOT_DIR/target-backend.log" 2>/dev/null || true
        exit 1
    fi
    if command -v curl >/dev/null 2>&1; then
        if curl -sf http://localhost:9090/actuator/health >/dev/null 2>&1; then
            BACKEND_UP=1
            break
        fi
    fi
    sleep 2
done

if [[ "$BACKEND_UP" == "1" ]]; then
    echo "  后端已就绪: http://localhost:9090"
else
    echo "  [警告] 后端 120 秒内未就绪，请查看 target-backend.log"
fi
echo

# ---------- 6. 启动前端 ----------
echo "[6/6] 启动前端 (端口 5173)..."
(cd "$ROOT_DIR/myvue" && npm run dev) > "$ROOT_DIR/target-frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "  前端 PID: $FRONTEND_PID，日志: target-frontend.log"
sleep 5

if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    echo "  [错误] 前端进程已退出，请查看 target-frontend.log"
    tail -n 30 "$ROOT_DIR/target-frontend.log" 2>/dev/null || true
    cleanup
fi

echo
echo "============================================"
echo "   启动完成"
echo "============================================"
echo
echo "  前端: http://localhost:5173"
echo "  后端: http://localhost:9090"
echo "  健康: http://localhost:9090/actuator/health"
echo
echo "  默认账号: admin / 123456  (SUPER_ADMIN)"
echo "            testuser / 123456"
echo
echo "  按 Ctrl+C 停止所有服务"
echo

wait
