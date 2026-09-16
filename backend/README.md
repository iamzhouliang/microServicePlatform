# MicroService Platform

## 🏗️ 技术架构

<div align="center">

`Vue3` `Spring Cloud 2024` `Spring Cloud Alibaba 2023` `Nacos` `Sentinel` `MyBatis-Plus` `Sa-Token` `Redis` `MySQL`

</div>

### ✨ 核心特性

| 特性 | 描述 |
|------|------|
| 🔗 **链路追踪** | 支持 SkyWalking / Zipkin / Pinpoint 等多种方案 |
| 🎨 **优雅布局** | 简洁美观，多套主题任意组合搭配 |
| 🔐 **权限控制** | SAAS / 多租户 / RBAC 权限控制开箱即用 |
| 📡 **消息推送** | 基于 WebSocket + Redis 的分布式消息 |
| 🚪 **动态网关** | 支持 Redis / Nacos 配置，动态路由管理 |
| 📦 **插拔组件** | 按需引入 `micro-service-platform-plugin` 模块 |
| ⚡ **高性能** | 2M 网络下接口响应普遍 10-150ms |
| 📝 **标准文档** | 集成 SpringDoc (Swagger V3) |
| ⏰ **分布式任务** | 集成 snail-job 分布式调度 |
| 🤖 **AI 集成** | 基于 Langchain4j 集成 RAG / Tools / MCP（开发中） |

### 🚀 快速开发

Vue 开发只需几行代码即可完成单表 CRUD：

```vue
<template>
  <fs-crud ref="crudRef" v-bind="crudBinding"/>
</template>
```



---

## 🛠️ 快速开始

### 1. 编译安装

```bash

# 安装依赖（必须按顺序执行）
mvn install -f micro-service-platform-dependencies/pom.xml -DskipTests
mvn install -f micro-service-platform-framework/pom.xml -DskipTests
```

### 2. 环境准备

#### 方式一：Docker Compose（推荐）

```bash
# 创建网络
docker network create micro-service

# 一键启动所有服务
cd 附件/docker
docker-compose up -d
```

#### 方式二：手动安装

<details>
<summary>📦 <b>必要环境</b>（点击展开）</summary>

```bash
# 创建 Docker 网络
docker network create micro-service

# Redis
docker run -d --name redis \
  --net micro-service \
  -p 6379:6379 \
  --restart always \
  redis:7-alpine

# MySQL 8.0
docker run -d --name mysql \
  --net micro-service \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e TZ=Asia/Shanghai \
  -v mysql_data:/var/lib/mysql \
  --restart always \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci

# Nacos 2.x
docker run -d --name nacos \
  --net micro-service \
  -p 8848:8848 \
  -p 9848:9848 \
  -p 9849:9849 \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  --restart always \
  nacos/nacos-server:v2.3.0

# RabbitMQ（可选，消息总线需要）
docker run -d --name rabbitmq \
  --net micro-service \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin \
  --restart always \
  rabbitmq:3-management
```

</details>

<details>
<summary>🔧 <b>可选环境</b>（点击展开）</summary>

```bash
# Sentinel Dashboard（流量控制）
docker run -d --name sentinel \
  --net micro-service \
  -p 8858:8858 \
  -p 8719:8719 \
  --restart always \
  bladex/sentinel-dashboard

# SkyWalking（链路追踪）
# 1. Elasticsearch
docker run -d --name elasticsearch \
  --net micro-service \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  --restart always \
  elasticsearch:7.17.10

# 2. SkyWalking OAP Server
docker run -d --name skywalking-oap \
  --net micro-service \
  -p 11800:11800 \
  -p 12800:12800 \
  -e SW_STORAGE=elasticsearch \
  -e SW_STORAGE_ES_CLUSTER_NODES=elasticsearch:9200 \
  -e TZ=Asia/Shanghai \
  --restart always \
  apache/skywalking-oap-server:9.5.0

# 3. SkyWalking UI
docker run -d --name skywalking-ui \
  --net micro-service \
  -p 10086:8080 \
  -e SW_OAP_ADDRESS=http://skywalking-oap:12800 \
  -e TZ=Asia/Shanghai \
  --restart always \
  apache/skywalking-ui:9.5.0
```

</details>

### 3. SkyWalking 集成配置

<details>
<summary>🔍 <b>IDEA 开发环境配置</b>（点击展开）</summary>

**VM Options:**
```
-javaagent:/path/to/skywalking-agent/skywalking-agent.jar
-Dskywalking.agent.service_name=micro-service-platform-gateway
-Dskywalking.collector.backend_service=127.0.0.1:11800
```

**生产环境启动命令:**
```bash
# Gateway 服务
nohup java \
  -javaagent:/opt/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=micro-service-platform-gateway \
  -Dskywalking.collector.backend_service=127.0.0.1:11800 \
  -jar micro-service-platform-gateway.jar \
  > logs/gateway.log 2>&1 &

# IAM 服务
nohup java \
  -javaagent:/opt/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=micro-service-platform-iam \
  -Dskywalking.collector.backend_service=127.0.0.1:11800 \
  -jar micro-service-platform-iam.jar \
  --spring.profiles.active=prod \
  > logs/iam.log 2>&1 &
```

</details>

---

