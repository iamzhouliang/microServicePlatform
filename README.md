# MicroService Platform

> 基于 Spring Boot 4 + Spring Cloud 2025 + Spring Cloud Alibaba 2025 + Vben Admin 5 的开源 SaaS 多租户云平台脚手架。

## ✨ 项目特色

- **多租户 + RBAC**：字段级/Schema级/数据源级多种隔离策略，权限按钮级控制
- **动态网关**：基于 Nacos / Redis 的动态路由，白名单、限流、签名校验
- **认证 + 滑块**：Sa-Token 集成 + tianai-captcha 滑块验证码
- **工作流**：Warm Flow 流程引擎（待启用，按需引入）
- **AI/RAG**：基于 LangChain4j 的工作流、知识库、文档向量化
- **代码生成 + 在线表单**：基于 Freemarker + Sa-Token 的代码生成器、可视化表单设计器
- **链路追踪**：Spring Boot Admin 监控 + Prometheus/Micrometer 指标
- **存储**：MyBatis-Plus 多租户 + Dynamic Datasource + Hutool + LangChain4j

## 🛠️ 技术栈

### 后端

| 组件 | 版本 |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.3 |
| Spring Cloud | 2025.0.x |
| Spring Cloud Alibaba | 2025.0.0.x |
| MyBatis-Plus | 3.5.x |
| Sa-Token | 1.42.x |
| Hutool | 5.8.x |
| Nacos | 2.5.2（命名空间：`micro-service-platform`）|
| MySQL | 8.4 |
| Redis | 7.4 |
| RabbitMQ | 4.0 |
| tianai-captcha | 1.5.x |

### 前端

| 组件 | 版本 |
|---|---|
| Node.js | >=20.12 |
| pnpm | >=10 |
| Vue | 3.5 |
| Vite | 7.2 |
| TypeScript | 5.x |
| Vben Admin | 5.x |
| Ant Design Vue | 4.x |
| Pinia | 3.x |
| FastCrud | 内置 |
| Turbo | 2.x |

## 🏛️ 架构图

```
                    ┌──────────────────────┐
                    │  Nginx (生产)         │
                    └──────────┬───────────┘
                               │
                ┌──────────────▼──────────────┐
                │  Gateway :8000  (WebFlux)    │
                │  - 动态路由 (Nacos/Redis)     │
                │  - 限流 / 白名单 / 签名        │
                │  - Sa-Token 鉴权透传          │
                └──────────┬───────────────┘
                           │
       ┌───────────┬───────┼────────┬──────────┬──────────┐
       │           │       │        │          │          │
   ┌───▼───┐  ┌────▼───┐ ┌─▼────┐ ┌─▼─────┐ ┌─▼─────┐ ┌─▼─────┐
   │ IAM   │  │ Suite  │ │  WMS  │ │  AI   │ │Monitor│ │(待) │
   │ :15002│  │ :15001 │ │ :5006 │ │:15012 │ │ :5050 │ │     │
   │       │  │        │ │       │ │       │ │       │ │     │
   │ 用户  │  │ 文件  │ │ 仓储  │ │ RAG   │ │监控  │ │     │
   │ 角色  │  │ 代码  │ │ 库存  │ │ Tools │ │     │
   │ 权限  │  │ 表单  │ │       │ │ 智能体│ │     │
   └───────┘  └────────┘ └───────┘ └───────┘ └───────┘
```

## 📁 目录结构

```
microServicePlatform/
├── backend/                                 # 后端 Spring Boot 多模块
│   ├── micro-service-platform-dependencies/  # BOM 依赖管理
│   ├── micro-service-platform-framework/     # 15 个 Spring Boot starters
│   │   ├── common-spring-boot-starter
│   │   ├── common-framework-core
│   │   ├── db-spring-boot-starter            # MyBatis-Plus + 动态数据源 + 租户
│   │   ├── security-spring-boot-starter       # Sa-Token + OAuth2
│   │   ├── redis-plus-spring-boot-starter
│   │   ├── websocket-spring-boot-starter
│   │   ├── ai-spring-boot-starter / ai-harness-spring-boot-starter
│   │   ├── diff-log-spring-boot-starter
│   │   ├── i18n-spring-boot-starter
│   │   ├── mongodb-plus-spring-boot-starter
│   │   ├── easyexcel / pdf / feign-plugin / robot
│   ├── micro-service-platform-feign/         # 跨服务 Feign API 契约
│   │   ├── micro-service-platform-iam-api
│   │   └── micro-service-platform-suite-api
│   ├── micro-service-platform-gateway/       # 网关 :15000
│   ├── micro-service-platform-iam/           # 鉴权中台 :15002
│   ├── micro-service-platform-suite/         # 文件/代码生成/在线表单 :15001
│   └── micro-service-plugin/                 # 业务插件
│       ├── micro-service-platform-ai/        # RAG :15012
│       ├── micro-service-platform-wms/       # 仓储 :5006
│       └── micro-service-platform-monitor/   # Spring Boot Admin :5050
├── frontend/                                # 前端 Vue 3 + Vite monorepo
│   ├── apps/
│   │   └── web-antd/                         # 主应用
│   │       ├── src/views/microService/        # 业务页面
│   │       │   ├── platform/                  # 平台管理（租户/菜单/字典）
│   │       │   ├── system/                   # 系统管理（用户/角色/权限）
│   │       │   ├── wms/                      # 仓储管理
│   │       │   ├── ai/                       # AI 智能体
│   │       │   └── develop/                  # 开发工具
│   │       ├── src/api/                       # 请求客户端 + 业务 API
│   │       ├── src/router/                    # 路由 + 动态菜单 + 权限守卫
│   │       └── src/plugin/fast-crud/          # FastCrud 全局配置
│   ├── packages/                              # 共享包 (@core / effects / utils / stores)
│   └── internal/                              # 工程配置 (eslint / tsconfig / vite-config)
└── 附件/                                    # 部署支持
    ├── docker/                               # docker-compose.yml（MySQL/Redis/Nacos/RabbitMQ）
    ├── mysql/                                # SQL 脚本（micro-service-platform.sql 等）
    └── nacos/                                # Nacos 配置导出
```

## 🚀 快速开始

### 1. 启动基础设施

```bash
cd 附件/docker
docker network create micro-service   # 首次创建网络
docker-compose up -d                  # 启动 MySQL/Redis/Nacos/RabbitMQ
```

### 2. 初始化数据库

```bash
docker exec -i micro-service-mysql mysql -uroot -p123456 \
  -e "CREATE DATABASE IF NOT EXISTS \`micro-service-platform\` CHARACTER SET utf8mb4;"

docker exec -i micro-service-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 \
  micro-service-platform < 附件/mysql/micro-service-platform.sql
```

### 3. 上传 Nacos 配置

通过 Nacos 控制台 http://localhost:8848/nacos 上传以下配置到 `micro-service-platform` 命名空间：

- `cloud-default.yaml`
- `mybatis-plus-default.yaml`
- `security.yaml`
- `redis.properties`
- `micro-service-platform-iam.properties`
- `micro-service-platform-gateway.properties`
- `micro-service-platform-suite.properties`
- `micro-service-platform-wms.properties`
- `micro-service-platform-ai.properties`

### 4. 编译后端

按依赖顺序编译：

```bash
mvn install -f backend/micro-service-platform-dependencies/pom.xml -DskipTests
mvn install -f backend/micro-service-platform-framework/pom.xml -DskipTests
mvn install -f backend/micro-service-platform-feign/pom.xml -DskipTests

mvn package -f backend/micro-service-platform-iam/pom.xml -DskipTests
mvn package -f backend/micro-service-platform-gateway/pom.xml -DskipTests
mvn package -f backend/micro-service-platform-suite/pom.xml -DskipTests
mvn package -f backend/micro-service-plugin/micro-service-platform-wms/pom.xml -DskipTests
mvn package -f backend/micro-service-plugin/micro-service-platform-monitor/pom.xml -DskipTests
mvn package -f backend/micro-service-plugin/micro-service-platform-ai/pom.xml -DskipTests
```

### 5. 启动后端（按小→大顺序）

```bash
# Java 21 路径需显式指定（不能用 PATH 里的 java17）
JAVA21="D:/Java/java21/bin/java.exe"

JVM_FLAGS="-Xms256m -Xmx1536m -XX:+UseG1GC -XX:MaxDirectMemorySize=128m -XX:+ExitOnOutOfMemoryError"

cd backend/micro-service-platform-gateway/target && \
  nohup "$JAVA21" $JVM_FLAGS -Xmx1024m -jar micro-service-platform-gateway.jar > /tmp/gateway.log 2>&1 &

cd backend/micro-service-platform-suite/target && \
  nohup "$JAVA21" $JVM_FLAGS -jar micro-service-platform-suite.jar > /tmp/suite.log 2>&1 &

cd backend/micro-service-platform-iam/target && \
  nohup "$JAVA21" $JVM_FLAGS -jar micro-service-platform-iam.jar > /tmp/iam.log 2>&1 &

cd backend/micro-service-plugin/micro-service-platform-wms/target && \
  nohup "$JAVA21" $JVM_FLAGS -jar micro-service-platform-wms.jar > /tmp/wms.log 2>&1 &

cd backend/micro-service-plugin/micro-service-platform-monitor/target && \
  nohup "$JAVA21" $JVM_FLAGS -Xmx1024m -jar micro-service-platform-monitor.jar > /tmp/monitor.log 2>&1 &

cd backend/micro-service-plugin/micro-service-platform-ai/target && \
  nohup "$JAVA21" $JVM_FLAGS -Xmx2048m -jar micro-service-platform-ai.jar > /tmp/ai.log 2>&1 &
```

### 6. 启动前端

```bash
cd frontend
pnpm install
cp apps/web-antd/.env.example apps/web-antd/.env.development
# 按需修改 .env.development
pnpm run dev:antd
```

## 🔐 默认账号

```
用户名: admin
密码:   123456
租户:   0000
客户端: pc-web / pc-web
```

> ⚠️ **生产部署必须修改所有默认密码**（数据库 / Sa-Token / Spring Boot Admin）

## 🌐 访问地址

| 服务 | URL |
|---|---|
| 前端 | http://localhost:5173（或动态端口，看启动日志） |
| Gateway Swagger | http://localhost:15000/doc.html |
| IAM Swagger | http://localhost:15002/doc.html |
| Suite Swagger | http://localhost:15001/doc.html |
| WMS Swagger | http://localhost:5006/doc.html |
| AI Swagger | http://localhost:15012/doc.html |
| Monitor (Spring Boot Admin) | http://localhost:5050 |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 管理 | http://localhost:15672 |

## 📋 端口分配

| 服务 | 端口 |
|---|---|
| Gateway | 15000 |
| IAM | 15002 |
| Suite | 15001 |
| WMS | 5006 |
| AI | 15012 |
| Monitor | 5050 |
| MySQL | 3306 |
| Redis | 6379 |
| Nacos HTTP / gRPC | 8848 / 9848 / 9849 |
| RabbitMQ AMQP / Mgmt | 5672 / 15672 |

## 🤝 贡献

欢迎提交 Issue 和 PR。代码风格遵循各模块根目录的 `AGENTS.md` 与 `CLAUDE.md`。

## 📄 许可证

本项目基于 Apache License 2.0 开源，详见 [LICENSE](./LICENSE)。

---

> 由 iamzhouliang 维护。如有问题请提交 Issue。