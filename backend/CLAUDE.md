# CLAUDE.md

本仓库（`micro-service-platform`，开源 SaaS 多租户云平台）的编码与协作规则，**以 [AGENTS.md](./AGENTS.md) 为唯一来源**。开始任何任务前先读根 `AGENTS.md`；若改动所在顶层模块（或 `micro-service-plugin` 下插件）有模块级 `AGENTS.md`，再读模块级。本文件只是核心红线摘要，**与 `AGENTS.md` 冲突时以 `AGENTS.md` 为准**。

## 规则优先级

1. 用户当前明确指令。
2. 模块级 `AGENTS.md`（若存在）。
3. 根 `AGENTS.md`。
4. 本 `CLAUDE.md` 与 `.cursor/rules` 摘要（与 `AGENTS.md` 冲突以 `AGENTS.md` 为准）。
5. `dev-support` 配置、历史 CodeReview / 附件资料。

## 技术基线

- Java 21 + Spring Boot 4 + Spring Cloud 2025 + Spring Cloud Alibaba 2025；统一 `jakarta.*`，不要 `javax.*` 或 Spring Boot 2.x 写法。
- MyBatis-Plus、MySQL / PostgreSQL（DATASOURCE/SCHEMA/COLUMN 多租户隔离）、Redis、MongoDB、Sa-Token、Warm Flow、LangChain4j、Hutool。
- 包名：框架工具 `com.microservice.framework.*`，业务模块 `com.microservice.platform.<module>.*`；依赖版本在 `micro-service-platform-dependencies` 管理，子模块不私改版本。

## 核心红线（详见 AGENTS.md）

1. **查询与持久化**：单表/非关联的查询、计数、定点更新、分组统计一律用 MyBatis-Plus + `Wraps`/`SuperMapper`（`selectList`/`selectCount`/`selectMaps`/`lbU().set().eq()`，自动跳空），不手写等价 SQL 或 XML；逻辑删除交给 `@TableLogic`（用 `removeById`/`delete(Wraps...)`/`SuperMapper.delete(field,value)`，禁止手写 `SET deleted=1`，查询也不手写 `deleted=0`）；复杂关联、聚合、性能场景才写 SQL，且优先 `@Select`/`@Update` 注解、XML 作为最后手段并说明原因；未被引用的 Mapper 自定义方法及其 XML 及时删除；禁止循环内单条查（N+1）。
2. **分层与基类**：保持现有 MVC 分层（不引入 DDD）；Service 继承 `SuperServiceImpl`/`SuperService`，Entity 继承 `SuperEntity<T>`，分页用 `PageRequest` / `buildPage()`。
3. **工具**：优先 Hutool 与框架已有工具（`BeanUtilPlus`、`JacksonUtils` 等），不为一次性需求新增通用工具类、不重复造轮子；JSON 用 `JacksonUtils`。
4. **异常**：业务异常抛 `CheckedException`，禁止吞异常或用 `null` / 空集合 / 假默认值伪装成功。
5. **事务**：写操作/多表操作用 `@Transactional(rollbackFor = Exception.class)`；跨多数据源/租户切换用 `@DSTransactional`，注意事务边界。
6. **日志**：运行日志 `@Slf4j` + 占位符，禁止 `printStackTrace`/`System.out`；接口访问日志用 `@AccessLog`、业务字段变更日志用 diff-log `@DiffField`，不另起一套；不打印密码、token、secret、私钥、完整身份证/手机号。
7. **多租户与权限**：不绕过租户过滤、数据权限（`@DataScope`/`@DataColumn`）、Sa-Token、签名校验（明确系统级路径除外）；实体继承 `Entity`/`SuperEntity` 自带 `id`/`tenantId` 及 `deleted`、`createBy/createName/createTime`、`lastModifyBy/lastModifyName/lastModifyTime`（MyBatis-Plus 自动填充），不要手动赋值或绕过。
8. **REST 约定**：分页 `POST /page`（`@RequestBody`，`buildPage()`、返回 `IPage`）、详情 `GET /{id}/detail`、新增 `POST /create`（返回 void）、修改 `PUT /{id}/modify`、删除 `DELETE /{id}`；ID 走 `@PathVariable`；create 与 modify 不共用同一请求 DTO；写操作走全局响应包装，不手包 `R`/`Result`（网关 WebFlux 例外）。
9. **契约**：Feign API、DTO、枚举值、字段名、序列化格式属对外契约，改动前查调用方；不把内部 Entity 泄漏为接口返回；包装类型不随意改成 primitive。
10. **枚举与常量**：枚举优先实现 `DictEnum`；魔法值集中到常量/枚举/配置。
11. **命名**：表/字段 snake_case，Java lowerCamelCase；检查英文拼写，不扩散错误拼写（已存在的包名/契约名属契约级，改名需评估调用方）。

    

## 高风险（按 L2 处理，先读调用链）

数据库/迁移、认证 / Sa-Token、网关路由 / 请求头透传（响应式，禁阻塞）、Feign 契约、多租户 / 数据权限 / 动态数据源（`@DSTransactional`）、WMS 库存 / 出入库流水、TMS 结算 / 费用计算、Workflow 流程状态、AI/RAG 异步与外部依赖、`micro-service-platform-framework` 公共行为。涉及这些先梳理影响范围，必要时在 `.claude/agents/` 或 `docs/` 记录决策与验证。

## 验证与安全边界

- 按 `AGENTS.md`「验证规则」做与风险匹配的聚焦编译/检查（如 `mvn -f <module>/pom.xml -DskipTests compile`）；无法验证时如实报告尝试的命令、失败现象、风险与下一步。
- 若 Maven 大面积报 Lombok 生成方法、枚举构造器、builder 或 `@Slf4j log` 缺失，先判断为注解处理/toolchain 配置问题，不要为消除症状批量改 model 类。
- 未经用户明确批准：不 `git commit` / `git push`、不改写历史、不做 `DROP` / 批量 `DELETE` 等破坏性 DB 操作、不调整 `micro-service-platform-framework` 公共行为、不输出真实凭证。维护者确认统一联系。

## 本仓库高频踩坑（Claude 专用）

- **Java 21 路径硬编码**：用 `D:/Java/java21/bin/java.exe`（不是系统 PATH 里的 java17）；class 文件是 Java 21 编译（version 65.0），用 java17 跑会 `UnsupportedClassVersionError`。
- **服务启动顺序**（小→大，避免 Windows 虚拟地址碎片累积）：Gateway → Suite → IAM → WMS → Monitor → AI，每个 sleep 25-30s 等上一启动完。改 `application.yml` 后**必须重新 `mvn package`**（fat jar 内嵌 config），否则启动用的还是旧 jar 里的值。
- **4+ JVM 必须 JVM flags**：`-XX:+UseG1GC -XX:MaxDirectMemorySize=128m -XX:+ExitOnOutOfMemoryError`；不加的话，在 Windows 上 4 个 Java 进程一起跑会撞上虚拟地址碎片，第 N+1 个 JVM 在 Go runtime 初始化阶段 `mheap.sysAlloc` 失败（"Native memory allocation failed"）。
- **Docker 容器（MySQL/Redis）TCP 握手会失效**：表现为 MySQL `EOFException: Can not read response from server`，Redis Lettuce `Connection closed prematurely`。Windows + Docker Desktop 已知问题。修法：`docker restart micro-service-mysql` 或 `micro-service-redis`；Nacos 卡死同样 `docker restart micro-service-nacos`。
- **Nacos 端口**：生产/标准 = `8848`（HTTP）+ `9848`（gRPC）。Windows 本机若 `netsh int ipv4 show excludedportrange` 显示 8848 在 8635-9134 区间，改用 `18848`+`19848`+`19849`（同时改 Nacos 容器 port mapping + 6 个 `application.yml` + repackage 所有 jar）。
- **菜单 component 路径**：`sys_resource.component` 必须 `/microService/...`（驼峰）匹配前端 Vben `normalizeViewPath`；老格式 `/micro-service/...` 会让所有动态菜单点进去 fallback 到 not-found.vue。

完整规则见 **[AGENTS.md](./AGENTS.md)**。
