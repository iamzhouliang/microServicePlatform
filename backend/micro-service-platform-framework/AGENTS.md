# AGENTS.md — micro-service-platform-framework 模块规则

> 本文件只写 `micro-service-platform-framework` 的差异化规则。通用编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。
>
> 最高约束：framework 是全仓库公共基座，被所有业务模块依赖，包名统一 `com.microservice.framework.*`。**任何公共行为改动按 L2 高风险处理，且需明确需求 + 维护者【 / 】确认**（见根 `AGENTS.md`「安全边界」）。

## 模块职责

公共 starter 与核心工具集合：

- `common-framework-core`：核心工具与基础实体（`com.microservice.framework.commons`：`BeanUtilPlus`、`JacksonUtils`、`exception.CheckedException`、`entity.SuperEntity`、`entity.DictEnum`、`annotation.log.AccessLog`）。
- `common-spring-boot-starter`：通用自动配置。
- `db-spring-boot-starter`：MyBatis-Plus 增强（`mybatisplus.wrap.Wraps`、`mybatisplus.ext.SuperService`/`SuperServiceImpl`/`SuperMapper`、`mybatisplus.page.PageRequest`）、多租户与动态数据源（`dynamic.DynamicDataSourceHandler`、`utils.TenantHelper`）、数据权限（`mybatisplus.datascope.annotation.DataScope`/`DataColumn`）。
- `security-spring-boot-starter`：安全/认证上下文（`com.microservice.framework.security`）。
- `redis-plus-spring-boot-starter`：Redis / 缓存 / 分布式锁。
- `mongodb-plus-spring-boot-starter`：MongoDB。
- `i18n-spring-boot-starter`：国际化（`@I18nMethod`、`@I18nField`）。
- `easyexcel-spring-boot-starter`：EasyExcel 导入导出。
- `diff-log-spring-boot-starter`：变更日志（`@DiffField`、`DiffLogInterceptor`）。
- `ai-spring-boot-starter`：AI / LangChain4j 基础。
- `pdf-spring-boot-starter`：PDF 能力。
- `feign-plugin-spring-boot-starter`：Feign 增强（请求头透传、系统级 token 等）。
- `websocket-spring-boot-starter`：WebSocket。
- `robot-spring-boot-starter`：机器人/通知。

## 改动前必读

- 根 `../AGENTS.md`「安全边界」「多租户、数据权限与安全」。
- 改 starter 前先看其自动配置类、`@ConditionalOn`、默认配置项与至少一个下游用法。

## 高风险点（一律 L2 + 维护者确认）

- **公共工具契约**：`Wraps`/`SuperService(Impl)`/`SuperEntity`/`PageRequest`/`BeanUtilPlus`/`JacksonUtils`/`CheckedException`/`TenantHelper`/`DictEnum` 是全仓库 API，方法签名、行为、默认值变更会波及所有模块，禁止随意改。
- **自动配置与默认值**：starter 的 `@ConditionalOn`、Bean 默认、配置 key 改动要兼容老配置，说明升级影响。
- **数据权限与租户/动态数据源**：`db`/`security` starter 的数据权限、租户隔离（DATASOURCE/SCHEMA/COLUMN）、动态数据源是正确性基座，改动按高风险全链路验证。
- **Feign 增强**：`feign-plugin` 的请求头透传、系统级 token 改动影响所有跨服务调用。
- **序列化/返回/分页基础**：统一返回、分页、JSON（`JacksonUtils`）行为改动影响所有接口契约。
- **不随意新增 starter**：新增公共能力先评估是否应进 framework，并联系维护者。

## 数据权限用法（约定）

业务模块按需选用 `@DataScope` / `@DataColumn`（`com.microservice.framework.db.mybatisplus.datascope.annotation`），不要手写等价 SQL 过滤；手写 SQL 时务必让 alias 与数据权限列一致。

## 文档 / SQL 归档

- framework 一般不放业务 SQL；跨 starter 规范放仓库级 `docs/`，单个 starter 的说明放对应 starter 配置类注释或 `README`。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../AGENTS.md`](../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

编译被改 starter，并至少编译一个下游业务模块：

```bash
mvn -f micro-service-platform-framework/<被改 starter>/pom.xml -DskipTests install
mvn -f micro-service-platform-iam/pom.xml -DskipTests compile   # 或其它下游模块
```

公共行为改动需说明对所有下游的兼容性与回滚方式。
