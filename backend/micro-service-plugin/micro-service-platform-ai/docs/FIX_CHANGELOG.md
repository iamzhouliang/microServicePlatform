# micro-service-platform-ai 缺陷整改 - 本轮修复记录

配套报告：`CODE_AUDIT_REPORT.md`。本轮聚焦确定性高、风险低、影响大的 P0/P1/P2 缺陷，均已通过 `mvn compile` + `mvn test-compile` + 相关单测（42 项）验证。架构级改造（迭代节点子图执行、PARALLEL 真并发、缓存体系重构、langchain4j 异步化、全量权限注解体系）需单独立项，本轮未纳入，仍保留在报告中。

## 已修复清单

| 编号 | 级别 | 问题 | 改动文件 |
|---|---|---|---|
| DATA-2 | P0 | 7 个实体 JSON 字段 `typeHandler` 缺 `autoResultMap`，查询静默为 null | `ModelEntity`/`McpServer`/`KnowledgeBase`/`KnowledgeChunk`/`KnowledgeItem`/`VectorMetadata`/`VectorizationTask` |
| DATA-3 | P0 | `updateStatusById` 引用不存在的 `status`/`updated_at` 列且异常被吞（死代码） | `ConversationMessageMapper` / `ConversationMessageService(Impl)` |
| RAG-1 | P0 | 知识条目乐观锁误用（手工 version+1）导致所有更新静默失败 | `KnowledgeItemServiceImpl` |
| FLOW-3 | P0 | `ExpressionEvaluator` 用全功能 SpEL 导致 RCE | `ExpressionEvaluator` |
| FLOW-1 | P0 | IF_ELSE 只实现 EQUALS/NOT_EQUALS，无 else 时激活全部分支 | `WorkflowRuntimeExecutor` |
| SEC-2 | P0 | 普通文本对话不校验 conversationId 归属（横向越权） | `ChatServiceImpl` |
| FLOW-4/SEC-5 | P0/P1 | HTTP 节点 SSRF 可绕过 + RestTemplate 无超时/跟随重定向/被负载均衡 | `HttpNodeExecutor` |
| SEC-6 | P1 | DocExtractor 任意本地文件读取（LFI） | `DocExtractorNodeExecutor` |
| CHAT-6 | P1 | "清空会话消息"不删消息、删除会话留孤儿数据 | `ConversationServiceImpl` |
| SEC-1/CHAT-7 | P1 | MCP STDIO 可执行任意命令 + 客户端缓存竞态泄漏 | `McpConnectionManagerImpl` |
| SEC-7 | P2 | MCP `env`（含凭证）明文回显前端 | `McpServerConfigServiceImpl` |
| CMP | P2 | CompareUtils 数值比较用 double 相等，精度丢失 | `CompareUtils` |

## 关键改动说明

### P0 数据层
- **autoResultMap**：7 个实体 `@TableName` 增加 `autoResultMap = true`，使 `JacksonTypeHandler` 在查询时正确反序列化 Map/对象字段（模型维度/温度、MCP env、分片 metadata 等不再丢失）。
- **updateStatusById**：删除引用不存在列的坏 SQL 及其未被调用的 `updateMessageStatusAsync` 服务方法与接口声明。
- **乐观锁**：`update`/`updateDocument` 不再手工 `version+1`，改为传入当前版本号交由 MyBatis-Plus 乐观锁插件自增，并检查 `updateById` 返回值，0 行时抛"已被其他操作修改"。

### P0/P1 安全
- **SpEL RCE**：`evaluateSpel` 由 `StandardEvaluationContext` 改为 `SimpleEvaluationContext.forReadOnlyDataBinding()`，禁止 `T(...)` 类型引用、构造器、Bean 引用。
- **SSRF**：`isPrivateNetworkHost` 改为把主机名解析为真实 IP，用 `InetAddress` 判定 loopback/linkLocal/siteLocal/anyLocal/组播，并显式拦截 `169.254.0.0/16`（云元数据）与 `100.64.0.0/10`；无法解析的主机名一律拒绝；仅允许 http/https 协议；专用 `RestTemplate` 设置连接/读取超时并禁止自动重定向（同时不再复用 `@LoadBalanced` 实例）。
- **会话越权**：`handleTextChat` 进入即调用 `assertConversationOwnership` 校验会话归属。
- **LFI**：DocExtractor 移除按本地文件系统路径读取的分支，只允许 `wf_` 文件 ID 或 Base64。
- **MCP RCE 缓解**：STDIO 启动命令拒绝 shell 解释器（sh/bash/pwsh/cmd 等）；`getClient` 改 `computeIfAbsent` 原子创建避免连接/子进程泄漏。
- **MCP env 脱敏**：分页响应对 env 值统一掩码。

### P0 功能正确性
- **IF_ELSE**：条件评估委托 `CompareUtils.compare` 支持完整运算符集，支持分支级 AND/OR 与 `valueIsVariable`；未命中且无 ELSE 时返回哨兵值终止该路径，不再激活全部出边。

### P1 数据一致性
- **会话级联删除**：`clearMessages`/`remove` 真正删除 `ai_conversation_turn` 与 `ai_conversation_summary`。

### P2
- **数值精度**：CompareUtils 数值相等/比较改用 `BigDecimal`。

## 验证

```
mvn compile          -> BUILD SUCCESS（326 源文件）
mvn test-compile     -> BUILD SUCCESS（22 测试文件）
mvn test -Dtest=WorkflowRuntimeExecutorTest,CompareUtilsTest,NodeExecutorIntegrationTest
                     -> Tests run: 42, Failures: 0, Errors: 0
```

## 第二轮复核新增修复

对全模块做了第二遍"查漏补缺"（横向同类缺陷 + 第一轮薄弱区域深挖），新增修复：

| 编号 | 级别 | 问题 | 改动文件 |
|---|---|---|---|
| DATA-5 | P1 | `knowledge_base_ids` 用 `LongListTypeHandler` 逗号分隔存储（`varchar`），却用 `JSON_CONTAINS(... CAST(? AS JSON))` 查询，多知识库会话必抛 `Invalid JSON text` | `ConversationServiceImpl` / `ChatServiceImpl`（改 `FIND_IN_SET`） |
| DATA-6 | P2 | `ChatAgent`/`Conversation`/`AiSkill` 缺 `autoResultMap`；且 `StringListTypeHandler` 与 `LongListTypeHandler` 都用 `@MappedTypes(List.class)` 存在全局注册冲突，`List<Long>` 字段可能被 String 处理器错误反序列化 | 3 个实体补 `autoResultMap=true` |
| SEC-8 | P1 | `WorkflowApiKey` 管理接口无归属校验：任意用户可为他人 workflowId 签发/枚举/禁用/删除 API Key（越权 + 权限提升） | `WorkflowApiKeyServiceImpl`（新增 `requireOwnedWorkflow`） |
| SEC-9 | P2 | `WorkflowTemplate` 的 detail/export/按分类/从工作流创建 缺归属过滤，可越权读取或复制他人私有模板的完整 graph | `WorkflowTemplateServiceImpl`（新增 `checkReadable` + 源工作流归属校验） |
| SEC-10 | P1 | `TemplateNodeExecutor` Freemarker 用默认 `Configuration`，`?new` 未受限，工作流设计者可注入 `freemarker.template.utility.Execute` 实现 RCE（与 SpEL 同信任边界的新攻击面） | `TemplateNodeExecutor`（`ALLOWS_NOTHING_RESOLVER` + 关闭 API 内建） |

第二轮结论修正（采纳子agent纠正）：
- **模型 apiKey 并非明文回显**——`ModelDetailResp`/`ModelPageResp` 已用 `@Sensitive(PASSWORD)` 掩码；真正明文的是 MCP `env`（第一轮已修）。第一轮报告"模型 apiKey 明文回显"一条应删除。
- **模板导入不是反序列化 RCE**——绑定到具体类型 `WorkflowTemplateSaveReq`，无多态 gadget，降级为参数校验问题。
- **乐观锁静默失败仅 `KnowledgeItem` 一处**；`ConversationSummary.version` 无 `@Version`，只是普通计数器（丢更新，已记 P2）。

验证：`mvn compile` + `mvn test`（43 项，含 WorkflowTemplateServiceImplTest）全绿。

## 第三轮复核

第三轮聚焦"亲自证实未修 P1 + 扫未逐行读过的角落 + 风险模式横向搜索"，结论：核心缺陷已基本收敛，新增 2 处同类小修：

| 编号 | 级别 | 问题 | 改动文件 |
|---|---|---|---|
| FLOW-CMP2 | P2 | `ListOperatorNodeExecutor.compareValues` 用 `Double.compare` 比较数值，大整数/BigDecimal 精度丢失（与已修的 CompareUtils 同类，属另一处副本） | `ListOperatorNodeExecutor`（改 BigDecimal） |
| FLOW-LIMIT | P3 | `executeLimit` 负 offset/count 会使 `subList` 抛越界；且返回 subList 视图，下游修改回写原列表 | `ListOperatorNodeExecutor`（钳制负数 + 返回副本） |

第三轮亲自证实（未盲修，因需配套测试的设计改动）：
- **join 提前触发 P1 确认属实**：以 `Start→A→J` + `Start→B→C→J` 为例，`A` 完成时 `readyToExecute(J)` 的 `activatedSources={A}` 已 `allMatch(executed)`，`J` 会在 `C→J` 到达前提前执行，`VariableAggregator`/多入边 END 丢数据。平衡菱形图恰好正确（现有测试仅覆盖平衡场景故未暴露）。正确修复须区分"分支剪枝"与"未到达"，且不能破坏当前正常的 loop 回边，属设计级改动，须单独立项 + 补拓扑测试。

第三轮已核对无新问题：`AiStatisticsServiceImpl`（null 安全、limit 钳制、无除零）、`VariableResolver`、`AiStatistics` SQL、`parseX`/`get(0)` 调用点（均有保护或作用于内部生成值）。

## 第二轮新发现但未修（需单独立项）

- **P1 工作流 join 提前触发**：`WorkflowRuntimeExecutor.readyToExecute` 因"节点执行后才写 `activatedIncomingSources`、执行前已入 `executedNodeSet`"，导致 `activatedSources.allMatch(executed)` 恒真——**分支长度不等的 join / VariableAggregator / 多入边 END 会在最短分支到达时提前触发、静默丢数据**，且使 `isDeadlocked` 沦为死逻辑。平衡菱形图恰好正确（现有测试只覆盖平衡场景，故未暴露）。修复需引入"预期入边"概念并处理条件分支剪枝，属架构改动，须配套测试，不宜盲改。
- **P1 暂停/快照/恢复链路断裂 + 线程泄漏**：`getSnapshot` 与 `resumeFromSnapshot` 格式不一致、快照不含 context 变量与 loop 计数、暂停时旧工作线程永久阻塞（`waitIfPaused` 无超时）。
- **P2 批量接口无 `@Size` 上限**（FAQ/StructuredData/KnowledgeItem/Vectorization/WorkflowFile 的 batchCreate/upload）、**KnowledgeRetrieval 的 metadataFilters 计算后从不传入检索**、**QuestionClassifier 无默认兜底分类 + HashMap 迭代致路由不确定**、**WorkflowValidator 缺环检测/缺失分支边校验，校验通过但编译期可能抛错**、**ToolNode 不读取 toolName / 硬编码默认模型 ID=1**、**WorkflowFileService 临时文件无 TTL/清理**、**开放 API 执行记录以 null user/tenant 落库**。
- **P3**：CompareUtils 的 IN 仅支持 List、正则全匹配 + 无缓存(ReDoS)、CONTAINS 把 null 当 "null"；`CustomizeChatModelListener` debug 打印完整 prompt；`ToolController /refresh` 用 GET 触发写 + 枚举全部 bean；application.yml 硬编码 nacos 口令。

## 仍需单独立项（本轮未改）

- **DATA-1 多租户 include-tables 表名错误**：位于 Nacos 外部配置（`micro-service-platform-ai.properties`），需运维侧修正为真实表名并补齐全部业务表，代码无法单独修复。
- 迭代节点子图执行（FLOW-2）、PARALLEL 真并发（FLOW-10）、执行体移出事务（FLOW-5）、执行超时与状态回收（FLOW-4/7）、静态 MEMORY_STORE 泄漏（FLOW-2 内存）、向量任务死锁与一致性（RAG-1/2/3）、图谱 embedding/删除（RAG-4/5）、缓存体系改 Caffeine、langchain4j 异步化、全量 `@SaCheckPermission` 权限体系、`skipTests=true` 移除等，详见 `CODE_AUDIT_REPORT.md`。
