# AGENTS.md — micro-service-platform-wms 模块规则

> 本文件只写 `micro-service-platform-wms` 插件的差异化规则。通用工程与编码规范以仓库根 [`../../AGENTS.md`](../../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。

## 模块职责

仓储管理插件，包结构（`com.microservice.platform.wms`）：

- `basic`：仓库、库区、巷道、月台、储位、容器等基础档案。
- `matedata`：物料、品牌、分类、供应商、承运商、单位/单位换算等元数据（注意：包名沿用现有拼写 `matedata`，不要为"纠正拼写"重命名包，属契约级改动）。
- `inbound`：收货计划、入库单、收货流程。
- `outbound`：出库计划、出库流程。
- `stock`：库存、库存变动流水。

## 改动前必读

- 改库存/出入库前先读 `stock`、`inbound`、`outbound` 对应 service + 库存流水写入逻辑。
- 根 `../../AGENTS.md`「消息、任务与异步」「数据库、SQL 与持久化」。

## 高风险点（按 L2 处理）

- **库存正确性**：库存扣减/回补、`stock` 流水必须事务一致、可追溯，不能只更新最终库存而丢失流水。
- **出入库流程**：收货计划→入库单、出库计划→出库的状态流转与数量校验（计划量/在途量/完成量）要严谨。
- **容器/储位占用**：占用与释放需并发安全，关注幂等与重复触发。
- **审计性**：库存与单据变更保留操作流水与审计字段。

## 文档 / SQL 归档

- WMS 建表/初始化 SQL 归档到 `附件` 对应目录并写明环境；破坏性/迁移 SQL 说明用途、影响范围、回滚方式与 MySQL/PostgreSQL 兼容性。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../../AGENTS.md`](../../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

```bash
mvn -f micro-service-plugin/micro-service-platform-wms/pom.xml -DskipTests compile
```

库存/出入库改动需验证流水写入、数量校验、并发占用与回滚假设。
