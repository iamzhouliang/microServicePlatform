# AGENTS.md — micro-service-platform-monitor 模块规则

> 本文件只写 `micro-service-platform-monitor` 插件的差异化规则。通用工程与编码规范以仓库根 [`../../AGENTS.md`](../../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。

## 模块职责

监控服务（Spring Boot Admin），包结构（`com.microservice.platform.monitor`）：

- 当前为轻量监控服务，入口 `MonitorApplication`，主要通过依赖与配置集成 Spring Boot Admin / Actuator。

## 改动前必读

- 改动前先看本模块 `pom.xml` 依赖与 `application*.yml` / Nacos 配置（监控端点、安全、注册方式）。

## 高风险点（按 L2 处理）

- **端点安全**：Actuator / Admin 端点不得对外暴露敏感信息（环境变量、配置、堆栈），需鉴权保护。
- **凭证保护**：监控接入的账号、token 不入库不打印。
- **配置环境隔离**：监控配置区分本地/演示/生产，避免混用。

## 文档 / SQL 归档

- 监控无业务 SQL；接入/部署说明放 `附件` 或仓库级 `docs/`。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`（见根 [`../../AGENTS.md`](../../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

```bash
mvn -f micro-service-plugin/micro-service-platform-monitor/pom.xml -DskipTests compile
```

配置改动需确认监控端点鉴权与敏感信息不外泄。
