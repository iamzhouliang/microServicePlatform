# AGENTS.md — micro-service-platform-feign 模块规则

> 本文件只写 `micro-service-platform-feign` 的差异化规则。通用编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准。
>
> 本模块是跨服务对外契约，所有改动默认按「契约变更」对待。

## 模块职责

跨服务 Feign API 契约，按提供方拆分子模块：

- `micro-service-platform-iam-api`、`micro-service-platform-suite-api`。

每个 `*-api` 提供对应服务的 Feign 接口、请求/响应 DTO、枚举与服务名常量。

## 调用方式约定

- 跨内部服务调用统一走本模块的 Feign 契约；服务名用常量、同服务多接口用稳定 `contextId`。
- 调用外部第三方 HTTP 接口统一使用 Feign 或项目已采用的客户端，不要零散引入 `RestTemplate`/`OkHttp`/`HttpClient` 等新的客户端风格。
- 需要系统级权限调用时，使用 `feign-plugin-spring-boot-starter` 现有的系统 token / 请求头透传模式，不自行拼接 token。

## 改动前必读

- 根 `../AGENTS.md`「API、DTO 与 Feign 契约」。
- 改某接口前查提供方实现 + 所有消费方（业务模块与前端约定）。

## 高风险点（按 L2 处理）

- **契约稳定**：路径、方法、字段名、枚举值/顺序、包装类型、序列化格式是对外契约，改动先查提供方与消费方，优先新增而非改旧。
- **不泄漏内部 entity**：DTO 独立定义，不直接复用业务 entity 作为接口契约，不暴露敏感字段。
- **包装类型语义**：`Boolean`/`Integer`/`Long`/`String` 可能表达"未传/默认/不限/全部"等语义，不随意改成 primitive。
- **枚举一致性**：`code`/`value`/`name`/JSON 值与前端、数据库、字典、历史数据保持一致。
- **不重复造接口**：相似查询/回显/导出先搜索已有契约，能复用或收敛时优先收敛。

## 文档 / SQL 归档

- 契约层不放业务 SQL；Feign 契约变更说明放仓库级 `docs/`。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../AGENTS.md`](../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

编译被改 api 模块 + 提供方 + 至少一个消费方：

```bash
mvn -f micro-service-platform-feign/micro-service-platform-<svc>-api/pom.xml -DskipTests install
mvn -f micro-service-platform-<provider>/pom.xml -DskipTests compile
```

列出受影响消费方并说明风险。
