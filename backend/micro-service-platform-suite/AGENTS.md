# AGENTS.md — micro-service-platform-suite 模块规则

> 本文件只写 `micro-service-platform-suite` 的差异化规则。通用工程与编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准；规则优先级见根 `AGENTS.md`「规则优先级」，冲突以更高优先级为准。

## 模块职责

平台通用辅助能力，包结构（`com.microservice.platform.suite`）：

- `file`：文件 / 对象存储（OSS）上传、下载、管理（`OssFileService` 等）。
- `gen`：代码生成器，模板、表元数据、生成配置。
- `online`：在线表单 / 动态表单能力。

## 改动前必读

- 改文件存储前先读 `file` 的 `OssFileService` 与存储配置（多存储后端 / 多租户隔离约定）。
- 改代码生成器前先读 `gen` 的模板与表元数据读取逻辑，注意生成产物落地路径。
- 改在线表单前先读 `online` 的表单定义与动态存储模型。

## 高风险点（按 L2 处理）

- **文件存储**：上传/下载凭证、bucket、路径拼接、租户隔离、访问鉴权，不要泄漏直链或越权访问；不打印密钥。
- **代码生成**：生成的代码风格必须对齐根 `AGENTS.md`「编码规范示例」（`Wraps`、`SuperServiceImpl`、REST 约定、`@AccessLog` 等）；模板改动影响后续所有生成产物。
- **在线表单**：动态表单元数据与租户/权限隔离一致，避免动态 SQL 注入风险。

## 文档 / SQL 归档

- 文件存储 / 在线表单相关初始化 SQL 归档到 `附件` 对应目录并写明环境；破坏性或迁移 SQL 说明用途、影响范围、回滚方式与兼容性。
- AI/agent 的一次性调研、决策记录放 `.claude/agents/` 或 `docs/`，不塞进源码包（见根 [`../AGENTS.md`](../AGENTS.md)「文档、SQL 与 AI 产物归档」）。

## 聚焦验证

```bash
mvn -f micro-service-platform-suite/pom.xml -DskipTests compile
```

涉及契约时编译 `micro-service-platform-feign/micro-service-platform-suite-api` 与至少一个消费方；代码生成模板改动需用一张样例表验证生成产物是否符合规范。
