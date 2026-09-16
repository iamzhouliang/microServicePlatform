# AGENTS.md — micro-service-platform-gateway 模块规则

> 本文件只写 `micro-service-platform-gateway` 的差异化规则。通用工程与编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。
>
> 注意：网关是响应式（Spring Cloud Gateway / WebFlux）服务，**不要在过滤器链路引入阻塞式调用**（同步 JDBC、阻塞 HTTP、`Thread.sleep`、`.block()`）或常规 MVC 写法。

## 模块职责

统一网关，包结构（`com.microservice.platform.gateway`）：

- `filter`：路由透传、鉴权、链路追踪等过滤器。
- `route`：动态路由配置与管理。
- `rest`：网关自身对外的少量管理/查询接口（响应式控制器可返回 `Mono`/`Flux`）。
- `handler`：全局异常处理、响应处理。
- `configuration`：网关配置、CORS、安全策略等。
- `utils`：网关通用工具。

## 改动前必读

- 改动前先读相关 `filter` 的执行顺序（`Order`）与 `configuration` 对应实现。
- 鉴权/透传相关改动同时查 `micro-service-platform-iam` 的认证逻辑与 `feign-plugin` 请求头透传约定。

## 高风险点（按 L2 处理）

- **过滤器顺序**：`filter` 的 `Order` 影响鉴权、限流、透传、追踪，改动先确认顺序与短路逻辑。
- **鉴权与透传**：token 校验、签名、租户/用户头透传、白名单，不擅自放宽。
- **动态路由**：`route` 改动影响全站路由，先梳理影响范围与回滚。
- **响应式约束**：禁止阻塞调用；新增逻辑保持 Reactor 链路非阻塞。
- **链路追踪**：traceId 注入需与前端 / 下游约定一致。

## 文档 / SQL 归档

- 网关无业务 SQL；正式路由 / 安全策略变更说明放 `micro-service-platform-gateway/docs/` 或仓库级 `docs/`。
- AI/agent 的一次性调研、临时排查记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../AGENTS.md`](../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

```bash
mvn -f micro-service-platform-gateway/pom.xml -DskipTests compile
```

鉴权/路由改动需检查过滤器顺序、白名单、签名、token、租户头透传与限流行为，并确认无阻塞调用引入。
