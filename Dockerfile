# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先复制 pom 文件，利用 Docker 层缓存
COPY pom.xml .

# 下载依赖（离线模式）
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# ===== 前端构建阶段 =====
FROM node:20-alpine AS frontend-builder
WORKDIR /app/myvue
COPY myvue/package*.json ./
RUN npm ci
COPY myvue/ ./
RUN npm run build

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 挂载日志目录
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# 复制后端构建产物
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# 复制前端构建产物到静态资源目录
COPY --from=frontend-builder --chown=appuser:appgroup /app/myvue/dist /app/static

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9090/actuator/health || exit 1

EXPOSE 9090

# 必须设置的环境变量
# DB_PASSWORD: 数据库密码
# JWT_SECRET: JWT 签名密钥（至少32字符）
# NVIDIA_API_KEY: NVIDIA API Key
# REDIS_PASSWORD: Redis 密码（可选）
USER appuser

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "app.jar"]