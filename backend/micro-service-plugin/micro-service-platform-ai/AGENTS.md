# AGENTS.md — micro-service-platform-ai 模块规则

> 本文件只写 `micro-service-platform-ai` 插件的差异化规则。通用工程与编码规范以仓库根 [`../../AGENTS.md`](../../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。

## 模块职责

AI 能力插件（LangChain4j / RAG），包结构（`com.microservice.platform.ai`）：

- `controller`：对话、向量检索、知识库等对外接口。
- `core`：AI 核心能力（模型/Agent 接入、向量化、检索编排等）。
- `service`：知识库 item/chunk、对话/消息、Chat/Agent 等业务服务。
- `domain`：知识库、对话、向量等 entity、req、resp。
- `core/workflow/listener`：工作流执行事件监听（节点开始/结束、执行状态等）；文档解析、向量化等异步不走此监听，而是由 `service` 层经框架 `AsyncExecutor`（虚拟线程 + TTL 上下文）触发。
- `repository`：数据访问（含向量库 / 图库适配）。

## 改动前必读

- 改 RAG/向量前先读 `service` 的 knowledge item/chunk/vectorization service、`core` 的向量化与检索编排、`repository` 的向量库/图库适配。
- 改异步任务前先读对应入口与失败处理：向量化/文档解析在 `service` 层（`VectorServiceImpl` 等）的 `AsyncExecutor` 调用；工作流执行事件在 `core/workflow/listener`。
- 改 `repository`（RDB Mapper）前：本模块 Mapper 一律继承 `SuperMapper`，单表的条件查询、计数、定点更新、分组统计统一用 `Wraps` + `SuperMapper`（`selectList`/`selectCount`/`selectMaps`/`lbU().set().eq()`），逻辑删除交给 `@TableLogic`，**不要为这些场景新增 Mapper 自定义方法或 XML**；确需手写 SQL 时优先 `@Select`/`@Update` 注解（见 `ConversationMessageMapper`），细则见根 [`../../AGENTS.md`](../../AGENTS.md)「数据库、SQL 与持久化」。

## 高风险点（按 L2 处理）

- **异步与外部依赖**：RAG、向量库（Milvus 等）、图库（Neo4j 等）、模型调用、文档解析通常依赖异步或外部服务，失败模式必须可见，不吞异常、不静默降级。
- **数据一致性**：知识库 item ↔ chunk ↔ 向量需保持一致，重建/删除时避免悬挂数据。
- **凭证与配额**：模型 API key、外部服务凭证不入库不打印；注意调用配额与超时。
- **幂等**：文档解析、向量化等可重复触发任务需幂等。

## 文档 / SQL 归档

- AI 相关初始化 SQL / 向量库 schema 归档到 `附件` 对应目录并写明环境；破坏性/迁移操作说明用途、影响范围、回滚方式与兼容性。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../../AGENTS.md`](../../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

```bash
mvn -f micro-service-plugin/micro-service-platform-ai/pom.xml -DskipTests compile
```

RAG/异步改动需验证至少一个核心处理路径与失败/重试路径，确认外部依赖不可用时的可见失败。
