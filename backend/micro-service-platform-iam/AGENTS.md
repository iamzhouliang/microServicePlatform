# AGENTS.md — micro-service-platform-iam 模块规则

> 本文件只写 `micro-service-platform-iam` 的差异化规则。通用工程与编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。

## 模块职责

认证授权与平台基础数据中台，包结构（`com.microservice.platform.iam`）：

- `auth`：登录认证、Sa-Token、登录策略、登录监听、凭证支持。
- `base`：基础数据、全局配置、字典、消息通知策略（`service.strategy`，如邮件/默认通知）。
- `system`：用户、角色、菜单、权限、组织、定时任务。
- `tenant`：多租户初始化与隔离。

## 改动前必读

- 改动认证/权限前先读 `auth`、`system` 对应 service + 至少一个调用方。
- 多租户相关改动先读 `tenant` 与 framework 的 `TenantHelper`、动态数据源实现。
- 根 `../AGENTS.md`「多租户、数据权限与安全」。

## 高风险点（按 L2 处理）

- **认证与会话**：登录、token、Sa-Token 注解、签名、登录策略/监听，改动先梳理调用链与白名单（含网关侧）。
- **RBAC**：用户/角色/菜单/权限/组织，注意数据权限（`@DataScope` / `@DataColumn`）与历史数据。
- **多租户**：`tenant` 初始化、隔离、`TenantHelper`、动态数据源；禁止绕过租户过滤（明确系统级路径除外）。
- **字典与消息**：`DictEnum`、字典变更影响历史数据/导入导出/前端筛选；消息模板/通知策略变更查调用方。
- **基础数据被全局引用**：`base`/`system` 数据被其它服务 Feign 依赖（`micro-service-platform-iam-api`），字段改动查契约与回显。

## 文档 / SQL 归档

- 权限/菜单/租户初始化等 SQL 归档到 `附件` 对应目录，并写明目标环境；破坏性或迁移 SQL 必须写明用途、影响范围、回滚方式与 MySQL/PostgreSQL 兼容性（见根 [`../AGENTS.md`](../AGENTS.md)「文档、SQL 与 AI 产物归档」与「安全边界」）。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包。

## 聚焦验证

```bash
mvn -f micro-service-platform-iam/pom.xml -DskipTests compile
```

涉及契约时编译 `micro-service-platform-feign/micro-service-platform-iam-api` 与至少一个消费方；鉴权/租户改动需检查过滤器顺序、白名单、token、租户头透传。
