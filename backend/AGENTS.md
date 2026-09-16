# AGENTS.md

本文件是 Claude、Codex 等 coding agent 在本仓库工作的项目级操作指南，是本仓库 agent 规则的**唯一权威来源**。

它综合了：

- 当前 `micro-service-platform` 仓库的本地项目约定与真实代码写法。
- 维护者提供的附件全局工作流说明。
- Karpathy 风格 coding-agent 规则：澄清不确定性、保持简单、避免顺手改无关代码、用可验证结果证明行为。

## 项目概览

MicroService Platform 是「全网最优秀、最简单、最漂亮的开源 SaaS 多租户云平台架构」，当前版本基于 Java 21 构建，技术栈包括 Spring Boot 4、Spring Cloud 2025、Spring Cloud Alibaba 2025、Sa-Token、MyBatis-Plus、Nacos、Redis、MySQL/PostgreSQL，并通过插件模块提供 Workflow（Warm Flow）、WMS、TMS、Monitor、AI/RAG 等能力。

顶层模块：

- `micro-service-platform-dependencies`：统一依赖与插件版本管理（BOM），项目模块的 parent，子模块不私自改版本号。
- `micro-service-platform-framework`：通用 framework starters 与核心工具（包名 `com.microservice.framework.*`）。
- `micro-service-platform-feign`：跨服务共享的 Feign API 契约，按提供方拆分 `*-api` 子模块。
- `micro-service-platform-gateway`：Spring Cloud Gateway 响应式网关服务。
- `micro-service-platform-iam`：身份认证、租户、RBAC、权限、字典、消息与基础数据。
- `micro-service-platform-suite`：文件、代码生成、在线表单等 suite 能力。
- `micro-service-plugin`：可选业务/应用插件分组目录（非聚合 pom），下含 `micro-service-platform-wms`、`micro-service-platform-ai`、`micro-service-platform-monitor`。
- `dev-support`：checkstyle、IDEA style、Spotless 配置与 license header。
- `附件`：本地部署、SQL、Nacos、Nginx、Docker 等支持文件。

> 包名约定（务必区分）：框架层工具统一在 `com.microservice.framework.*`（如 `com.microservice.framework.commons`、`com.microservice.framework.db`、`com.microservice.framework.security`）；业务模块统一在 `com.microservice.platform.<module>.*`（如 `com.microservice.platform.iam`、`com.microservice.platform.wms`）。

## 技术基线

- Java 21 + Spring Boot 4 + Spring Cloud 2025 + Spring Cloud Alibaba 2025；统一使用 `jakarta.*`，不要 `javax.*` 或 Spring Boot 2.x 写法。
- 持久层 MyBatis-Plus + MySQL / PostgreSQL，支持 DATASOURCE / SCHEMA / COLUMN 多种租户隔离；缓存与分布式锁 Redis；文档/日志 MongoDB；消息 RocketMQ；认证 Sa-Token；工作流 Warm Flow；AI LangChain4j / RAG；通用工具 Hutool。
- 框架工具统一来自 `com.microservice.framework.*`，不是历史包名或其它脚手架包名。依赖版本统一在 `micro-service-platform-dependencies` 管理。
- 网关 `micro-service-platform-gateway` 是 WebFlux 响应式服务，禁止在过滤器链路引入阻塞调用（同步 JDBC、阻塞 HTTP、`Thread.sleep`、`.block()`）与常规 MVC 写法。

## 核心工作规则

- 先思考，再编辑。非平凡任务开始前，先识别目标文件、期望行为、验证方式与风险。
- 保持简单。优先选择与现有模式一致的最小正确改动。
- 外科手术式改动。不要格式化、重命名、重排或重构无关代码。
- 保护用户已有工作。较大改动前先看 `git status --short`，除非用户明确要求，绝不回退不是你做的改动。
- 管理不确定性。如果需求、数据结构或副作用不清晰，且无法从代码安全推断，应先问一个简洁问题，或明确说明假设。
- 用证据验证。没有新鲜命令输出或直接检查结果时，不要声称构建、测试、修复已经成功。
- 暴露真实失败。不要用假默认值、吞异常、模拟成功数据或静默降级掩盖真实问题。
- 任何数据库、认证、权限、租户隔离、动态数据源、库存、结算、工作流状态相关改动都按高风险处理。

## 规则源维护

- `AGENTS.md` 是本仓库 agent 规则主来源。
- 每个顶层模块（及 `micro-service-plugin` 下各插件）有一份模块级 `AGENTS.md`，只写本模块差异。
- `CLAUDE.md` 是「以 `AGENTS.md` 为唯一来源」的红线摘要，不承载独有规则。
- 新增、删除或调整长期项目规则时，先更新 `AGENTS.md`，再按需要同步 `CLAUDE.md` 与 `.cursor/rules/*` 摘要。
- 如果本文件、其他 agent 摘要和用户当前指令冲突，优先遵循用户当前指令；同时说明冲突点、取舍原因和影响。
- 不要把一次性任务背景写成永久规则。只有能长期约束本仓库开发质量的约定才加入本文件。

## 规则优先级

冲突时从高到低：

1. 用户当前明确指令。
2. 模块级 `AGENTS.md`（如所在顶层模块/插件下存在）。
3. 仓库根 `AGENTS.md`（本文件）。
4. 根 `CLAUDE.md` 与 `.cursor/rules/*` 摘要（与 `AGENTS.md` 冲突一律以 `AGENTS.md` 为准）。
5. `dev-support` 配置与历史 CodeReview、附件等专项资料。

## 模块级 AGENTS.md 与 CLAUDE.md

- 每个顶层模块（`micro-service-platform-framework`、`micro-service-platform-feign`、`micro-service-platform-gateway`、`micro-service-platform-iam`、`micro-service-platform-suite`）以及 `micro-service-plugin` 下每个插件（`micro-service-platform-wms`、`micro-service-platform-ai`、`micro-service-platform-monitor`）都有一份模块级 `AGENTS.md`，**只写本模块差异**（职责、改动前必读、高风险点、文档归档、聚焦验证），通用规范一律指向根 `AGENTS.md`。
- 根 `CLAUDE.md` 是红线摘要，不承载独有规则。
- 同一条规则只维护一处：通用规则进根 `AGENTS.md`，模块差异进模块级 `AGENTS.md`，避免多处漂移。开始任务前先读根 `AGENTS.md`，再读所在模块级 `AGENTS.md`。

## 品牌与版权保护

- 保留 dev-support/spotless/license-header` 约定。新增 Java 文件时遵循当前模块的 license、package、format、Lombok 和注释风格。
- 保留现有中文 README、中文注释和业务术语，不要批量改成英文或通用模板名。

## 任务分级

使用与风险匹配的最轻流程。

- L0：小型单文件修复、注释、文档或明显局部改动。读取相关代码，修改，运行聚焦检查。
- L1：多文件改动、服务逻辑、API 契约、配置或测试。收集上下文，简短规划，小步编辑，验证受影响模块。
- L2：跨模块架构、数据库/迁移、认证/安全、租户隔离、动态数据源、库存、结算、工作流流程、部署或核心业务流程改动。必要时在 `.claude/agents/` 或 docs 中记录关键决策与验证细节。

## 服务治理与模块边界

- 顶层服务和模块边界要稳定。新增服务、合并服务、拆分模块、引入 Maven 子模块都按 L2 处理，先说明必要性和影响范围，再与维护者确认。
- 业务能力归口清晰：基础认证/租户/权限进 `iam`，文件/代码生成/在线表单进 `suite`，可插拔业务（wms/ai/monitor）进 `micro-service-plugin` 下对应插件。新增插件先评估是否应作为独立插件。
- 包、Controller、Service、DTO 命名按业务领域和资源名组织，不要按中文直译、临时标签或模糊包名堆放。边界不清时，先查现有包结构和调用方，再决定归属。
- 不要为了"包一层"新增空壳 Service、ServiceImpl、Controller 或 Feign 转发层。新增层次必须承担清晰职责，例如权限、事务、编排、转换、幂等或跨服务契约隔离。
- 通用能力优先进 `micro-service-platform-framework` 对应 starter，不要在业务模块里重复造与 framework 同义的工具/配置。

## 仓库常用命令

本仓库根目录没有聚合 `pom.xml`。默认使用活动 shell 中的 Maven，并按模块 `-f` 执行。

常见初始化/构建顺序：

```bash
mvn install -f micro-service-platform-dependencies/pom.xml -DskipTests
mvn install -f micro-service-platform-framework/pom.xml -DskipTests
mvn install -f micro-service-platform-feign/pom.xml -DskipTests
```

聚焦模块编译示例：

```bash
mvn -f micro-service-platform-gateway/pom.xml -DskipTests compile
mvn -f micro-service-platform-iam/pom.xml -DskipTests compile
mvn -f micro-service-platform-suite/pom.xml -DskipTests compile
mvn -f micro-service-plugin/micro-service-platform-wms/pom.xml -DskipTests compile
mvn -f micro-service-plugin/micro-service-platform-ai/pom.xml -DskipTests compile
mvn -f micro-service-plugin/micro-service-platform-monitor/pom.xml -DskipTests compile
```

Feign 契约模块编译示例：

```bash
mvn -f micro-service-platform-feign/micro-service-platform-iam-api/pom.xml -DskipTests compile
mvn -f micro-service-platform-feign/micro-service-platform-suite-api/pom.xml -DskipTests compile
```

格式化和静态检查（配置位于 `dev-support`，优先选择 `pom.xml` 中已配置对应插件的模块）：

```bash
mvn -f micro-service-platform-iam/pom.xml spotless:check
mvn -f micro-service-platform-iam/pom.xml checkstyle:check
mvn -f micro-service-platform-suite/pom.xml spotless:check
mvn -f micro-service-platform-gateway/pom.xml checkstyle:check
```

如果依赖模块尚未安装，先按上面的初始化顺序安装 `dependencies`、`framework`、`feign`。如果命令失败，不要改口说已经通过；记录失败命令、关键错误和下一步判断。

本地常见坑：如果 Maven 同时报出大量 Lombok 生成方法、枚举构造器、builder 或 `@Slf4j log` 缺失，优先怀疑注解处理/toolchain 配置，而不是批量修改业务类。先用小范围编译确认，再检查模块 compiler 配置。

## 文档、SQL 与 AI 产物归档

- 正式建表/迁移 SQL、Nacos、Docker、Nginx、租户初始化等支持文件归档到 `附件` 对应目录（如 `附件/mysql`），并写清目标环境，不要把演示、本地、生产配置混在一起，也不要散落在仓库根或源码树。
- 破坏性或迁移 SQL（`DROP`/批量 `DELETE`/Schema 重写）必须在文件内说明用途、影响范围、回滚方式与 MySQL/PostgreSQL 兼容性；执行需用户明确批准。
- AI/agent 的一次性调研、决策记录、临时计划放 `.claude/agents/` 或 `docs/`，不要塞进源码包，也不要把临时文档当作"必读入口"长期保留。
- 临时/备份代码（`*_bak`、注释掉的旧实现、压测入口）不留在生产源码树，确认无引用后删除（git 历史可追溯）。

## Java 与后端风格

- 使用 Java 21，但不要为了炫技引入难读语法。
- 遵循项目已有 MVC 分层，不要引入 DDD 分层或新的架构风格。
- 遵循项目已有 Spring Boot、Spring Cloud、MyBatis-Plus、Sa-Token、Lombok、Warm Flow 模式。
- 优先复用已有工具：`Wraps`、`LbqWrapper`、`SuperService`、`SuperServiceImpl`、`SuperMapper`、`SuperEntity`、`PageRequest`、`BeanUtilPlus`、`JacksonUtils`、`CheckedException`、`TenantHelper`、`DictEnum` 以及已有 mapper/service 约定。
- 工具类优先使用 Hutool 和框架已有工具（`com.microservice.framework.commons.*`）。不要为单次需求私自新增通用工具类。
- JSON 处理优先使用 `JacksonUtils`。不要在业务类里散落新的 `ObjectMapper`，除非已有局部配置无法满足需求，并说明原因。
- Bean/DTO/entity 转换优先使用 `BeanUtilPlus`、Hutool `BeanUtil` 或模块内已有转换方式。不要为了单次转换引入新的 mapping 框架。
- 业务异常使用 `CheckedException` 和现有错误返回约定。不要用 `null`、空集合或吞异常伪装成功。
- 分页请求遵循 `PageRequest` / `buildPage()` 以及模块内现有分页模式，避免手写重复分页参数解析。
- 查询优先使用 MyBatis-Plus wrapper、mapper/service 既有方法。非关联查询不要写 SQL；手写 SQL 需要能解释为什么 wrapper 不适合。
- 事务使用 Spring `@Transactional(rollbackFor = Exception.class)`；跨多数据源/租户切换的写操作使用 `@DSTransactional` 并与周边代码保持一致，注意事务边界。
- API/controller 遵循已有 `@Tag`、`@Operation`、`@AccessLog`、Sa-Token 权限注解、请求/响应 DTO、校验分组约定。
- Feign 契约保持 DTO 稳定，除非模块已有类似模式，否则不要把内部 entity 泄漏为接口契约。
- MyBatis mapper XML 放在 Java 源码旁边时，要符合模块 `src/main/java` 复制 `**/*.xml` 的资源约定。
- 运行日志使用 Lombok `@Slf4j` 和参数化消息（占位符），禁止 `printStackTrace()`、`System.out/err.println`；不要记录密码、token、secret、私钥、完整身份证/手机号或完整敏感配置。
- 项目已有分层日志体系，不要另起一套：接口访问日志用 `@AccessLog`（`com.microservice.framework.commons.annotation.log.AccessLog`）；业务字段变更日志用 diff-log starter（`@DiffField`、`DiffLogInterceptor`、`DefaultDiffItemsToLogContentService`）。
- 注释用于解释意图、业务规则、不变量、边界条件或非显然取舍。不要添加只复述下一行代码的噪音注释。

## 编码规范示例（带正反例）

以下示例统一按 `micro-service-platform` 真实写法给出，新增/治理代码请对齐。代码块仅为示意。

### 一、MyBatis-Plus 查询

统一用框架 `Wraps`（`com.microservice.framework.db.mybatisplus.wrap.Wraps`），它会自动跳过空值条件，不必再用外层 `if` 包裹。

```java
// 反例：new 原生 wrapper + 多余 if 包裹
LambdaQueryWrapper<Warehouse> w = new LambdaQueryWrapper<>();
if (StrUtil.isNotBlank(req.getName())) {
    w.like(Warehouse::getName, req.getName());
}
List<Warehouse> list = warehouseService.list(w);

// 正例：Wraps 自动跳空
List<Warehouse> list = warehouseService.list(
        Wraps.<Warehouse>lbQ()
                .eq(Warehouse::getCode, req.getCode())
                .like(Warehouse::getName, req.getName()));
```

单表/非关联查询走 MP API，不手写 SQL；循环内禁止单条查询（N+1），先批量取再按键映射。

```java
// 反例：循环内逐条查（N+1）
for (Order o : orders) { User u = userMapper.selectById(o.getUserId()); }
// 正例：批量查一次，组装 Map 后取值
Map<Long, User> userMap = userMapper.selectByIds(
        orders.stream().map(Order::getUserId).distinct().toList())
        .stream().collect(Collectors.toMap(User::getId, Function.identity()));
```

### 二、工具与转换

优先 Hutool 与框架工具，不为单次需求新增通用工具类。

| 场景 | 用 | 不要 |
|---|---|---|
| Bean 拷贝/转换 | `BeanUtilPlus`、Hutool `BeanUtil`、模块 convert | 自造 mapping 框架、单点引入 MapStruct |
| 字符串/集合 | Hutool `StrUtil`/`CollUtil` | 重复造轮子 |
| 日期/时间 | Hutool `DateUtil`/`LocalDateTimeUtil` 或 `java.time` | 静态 `SimpleDateFormat`（非线程安全）、`Calendar` |
| 金额/精度 | `BigDecimal`（指定精度/舍入）、Hutool `NumberUtil` | `double`/`float` 做加减乘除 |
| 第三方 HTTP | Feign / 项目已采用的客户端 | 自造 `HttpClientUtil`、零散 `RestTemplate` |
| JSON | `JacksonUtils` | 业务类里散落新 `ObjectMapper` |
| 固定线程池/常量 | `static final` | 无意义的 `@Bean`（仅包一层） |

### 三、封装与抽象（避免过度封装）

不要为了"包一层"新增空壳 `Service`/`ServiceImpl`/`Controller`/Feign 转发层；新增层次必须承担权限、事务、编排、转换、幂等或跨服务契约隔离等清晰职责。

### 四、分层、基类与 REST 路径

- Entity 继承 `SuperEntity<T>`（`com.microservice.framework.commons.entity.SuperEntity`），保留审计/逻辑删除字段与 `@TableName`；Service 实现继承 `SuperServiceImpl`、接口继承 `SuperService`。
- 资源名 + 复数 + 横杠；动作语义用 HTTP 方法表达。写操作返回 `void`、分页返回 `IPage<Resp>`，**走全局响应包装，不手动包 `R`/`Result`**（网关 WebFlux 例外）。create 与 modify 不共用请求 DTO。

| 操作 | 端点 |
|---|---|
| 分页 | `POST /warehouses/page`（`@RequestBody`，`buildPage()`） |
| 详情 | `GET /warehouses/{id}/detail`（ID 走 `@PathVariable`） |
| 只读查询/统计 | `GET /warehouses?status=xxx`（非分页只读优先 GET） |
| 新增 | `POST /warehouses/create`（返回 void） |
| 修改 | `PUT /warehouses/{id}/modify` |
| 删除 | `DELETE /warehouses/{id}` |

```java
// 正例：返回 void，走全局包装；构造器注入；Wraps 自动跳空
@RestController
@AllArgsConstructor
@RequestMapping("/warehouses")
@Tag(name = "仓库管理", description = "仓库管理")
public class WarehouseController {
    private final WarehouseService warehouseService;

    @PostMapping("/page")
    @Operation(summary = "仓库分页 - [DONE] - [作者]")
    public IPage<WarehousePageResp> pageList(@RequestBody WarehousePageReq req) {
        return warehouseService.page(req.buildPage(), Wraps.<Warehouse>lbQ()
                .like(Warehouse::getName, req.getName()))
                .convert(x -> BeanUtil.toBean(x, WarehousePageResp.class));
    }

    @PostMapping("/create")
    @AccessLog(module = "仓库管理", description = "新增仓库")
    @Operation(summary = "新增仓库 - [DONE] - [作者]")
    public void create(@Validated @RequestBody WarehouseSaveReq req) { warehouseService.create(req); }

    @PutMapping("/{id}/modify")
    @AccessLog(module = "仓库管理", description = "修改仓库")
    public void modify(@PathVariable Long id, @Validated @RequestBody WarehouseSaveReq req) { warehouseService.modify(id, req); }

    @DeleteMapping("/{id}")
    @AccessLog(module = "仓库管理", description = "删除仓库")
    public void delete(@PathVariable Long id) { warehouseService.removeById(id); }
}
```

改造前后对照（治理时参考）：

| 反例 | 正例 |
|---|---|
| `GET /getUserById?id=1` | `GET /users/{id}/detail` |
| `POST /save`、`POST /insert` | `POST /create` |
| `POST /update` | `PUT /{id}/modify` |
| `POST /delete` | `DELETE /{id}` |
| `GET /aiAbcDfg` | `GET /ai-abc-dfg` |

### 五、命名与拼写

- 表/字段 snake_case，Java lowerCamelCase；金额用 `BigDecimal`；枚举实现 `DictEnum`，魔法值集中到常量/枚举。
- 字段可用可理解的缩写，但禁止错误拼写与前后不一致命名。

### 六、注释与 Swagger

- 所有新增类保持标准类注释（含 `@author`、`@since`）；注释解释意图/业务规则/边界，不复述下一行代码。
- 接口沿用周边 `@Tag`、`@Operation`、`@AccessLog`、Sa-Token 校验注解；`@Operation(summary = "功能描述 - [DONE] - [作者]")` 跟随周边写法。

### 七、日志

`@Slf4j` + 占位符；禁止 `printStackTrace()`、`System.out/err.println`；不打印密码、token、secret、私钥、完整身份证/手机号。

```java
// 反例
catch (Exception e) { e.printStackTrace(); }
// 正例
catch (Exception e) { log.error("文件解析失败, fileId={}", fileId, e); }
```

### 八、异常

业务校验失败抛 `CheckedException`（带中文提示），禁止吞异常或裸 `throw new RuntimeException()`；MQ/异步需要重试才抛异常，并先确认幂等。

```java
// 反例
if (order == null) { throw new RuntimeException(); }
// 正例
if (order == null) { throw new CheckedException("订单不存在, orderNo=" + orderNo); }
```

### 九、Lombok 与卫语句（减少样板与嵌套）

- 充分利用 Lombok，不手写可由注解生成的样板：entity/DTO 用 `@Data`（或 `@Getter`/`@Setter`），Spring 组件用 `@Getter` + `@RequiredArgsConstructor`/`@AllArgsConstructor` 构造器注入。**不要手写字段 getter/setter**（手写会架空同名 `@Getter`/`@Data`，纯属冗余）；只有带转换/校验逻辑的取值赋值方法才单独写并命名清晰。
- 能提前返回的用卫语句（guard clause）降低嵌套：方法开头对 null/非法/空集合先 `if (x == null) return ...;`，避免 `if (x != null) { 大段逻辑 }` 把主体整体缩进。

```java
// 反例：手写 getter 架空 @Getter；深层嵌套
@Getter
private final Driver driver;
public Driver getDriver() { return driver; }   // 多余，@Getter 已生成

public void close() {
    if (driver != null) {            // 主体被整体缩进
        driver.close();
    }
}

// 正例：注解生成 getter + 卫语句提前返回
@Getter
private final Driver driver;        // 不再手写 getDriver()

public void close() {
    if (driver == null) {
        return;
    }
    driver.close();
}
```

### 十、改动后自检清单

- [ ] 单表查询用 `Wraps`，无 `new` 原生 wrapper、无多余 `if` 包裹、无循环内单条查。
- [ ] 写操作返回 `void`/分页返回 `IPage`，未手包 `R`/`Result`（网关除外）。
- [ ] REST 路径为资源名 + 横杠；create/modify 未共用 DTO。
- [ ] 构造器注入（`@AllArgsConstructor`/`@RequiredArgsConstructor` + `private final`），无 `@Autowired` 字段注入。
- [ ] Entity 继承 `SuperEntity`、Service 继承 `SuperServiceImpl`。
- [ ] 异常用 `CheckedException`，日志用 `@Slf4j` 占位符，无 `printStackTrace`/`System.out`。
- [ ] 命名/拼写一致，金额 `BigDecimal`，魔法值走枚举/常量。
- [ ] 未手写可由 Lombok 生成的 getter/setter/构造器样板；能提前返回处用卫语句，避免深层 `if` 嵌套。
- [ ] 事务、租户、数据权限、动态数据源边界已确认；高风险改动已聚焦编译 + 业务回归。

## 数据库、SQL 与持久化

- 表、字段、DTO、VO、PO、Entity、Mapper、Service、Controller 的命名和字段类型要一致；数据库字段遵循 snake_case，Java 字段遵循 lowerCamelCase。
- 单表 CRUD 优先使用 MyBatis-Plus API 和项目已有 mapper/service 方法。非关联查询不要手写 SQL；复杂关联、聚合或性能场景才写 XML/SQL，并说明原因。
- **单表的条件查询、计数、定点更新、分组统计一律用 Wrapper/`SuperMapper` 表达，不要为此新增 Mapper 自定义方法或 XML**（这些属于"可用 SuperMapper 解决却写了 SQL"的冗余）：
  - 条件查询 / 排序：`baseMapper.selectList(Wraps.<T>lbQ().eq(...).orderByDesc(...))`；取 top-N 用 `.last("LIMIT " + n)`（n 必须是数值，禁止拼接字符串参数）。
  - 计数：`SuperMapper.selectCount(T::getField, value)` 或 `selectCount(Wraps.<T>lbQ()...)`，不要写 `SELECT COUNT(*)`。
  - 定点更新：`baseMapper.update(null, Wraps.<T>lbU().set(T::getA, a).set(T::getB, b).eq(T::getKey, key))`，不要为改几个字段写 `UPDATE` XML。
  - 分组聚合：`baseMapper.selectMaps(Wraps.<T>q().select("col", "COUNT(*) AS cnt").eq(...).groupBy("col"))`，避免为 group by 写 XML。
- **逻辑删除统一走 `@TableLogic`**：删除用 `removeById` / `remove(Wraps...)` / `baseMapper.delete(Wraps...)` / `SuperMapper.delete(T::getField, value)`，严禁手写 `UPDATE ... SET deleted = 1`；查询也不要手写 `deleted = 0`（框架按实体自动追加逻辑删除与租户条件，手写 SQL 反而容易与之不一致）。
- 确需手写 SQL 时（复杂多表 join、窗口函数、DB 方言特性等 Wrapper 无法表达的场景），**优先用 `@Select`/`@Update` 注解**（参考 `ConversationMessageMapper`），仅当注解难以承载（大段动态 SQL、`<foreach>`/`<if>`/`<sql>` 复用等）才用 XML，并在改动说明里解释为什么 Wrapper/注解不适合。
- MyBatis XML 只在需要消除歧义或映射字段时使用别名和 `AS`；不要写死 `schema.table_name`，除非迁移脚本或特殊数据源明确需要并已说明。
- 死代码同步清理：未被任何调用方引用的 Mapper 自定义方法及其对应 XML 语句要一并删除（git 历史可追溯），不要保留"占位"或"以后可能用得到"的空壳 SQL。
- 新增索引命名遵循统一风格；涉及迁移时说明兼容性、回滚方式和 MySQL/PostgreSQL 差异。
- 不要随意新增缓存。确有性能需求时，先查项目已有缓存规范，明确 key、租户隔离、失效策略、并发一致性和回滚方案。
- 展示字段（名称、字典文本等）可按现有模式做适度冗余以减少重复查询并保留历史快照；新增冗余字段时说明来源、刷新时机和是否允许与主数据不一致。

## API、DTO 与 Feign 契约

- Feign API、controller 请求/响应 DTO、枚举值、字段名和序列化格式都属于对外契约。修改前先查调用方、前端约定、数据库字段和示例数据。
- `Boolean`、`Integer`、`Long`、`String` 等包装类型可能表达"未传/默认/继承/不限/全部"等业务语义，不要随意改成 primitive。
- 枚举的 code、value、name、ordinal、JSON 值要与前端、数据库、字典和历史数据保持一致。不要随意重排枚举常量。
- 新增展示字段时，同时考虑 `@Schema`、字典、国际化、数据权限、导出/列表响应和 Feign 回显是否需要同步。
- 跨服务 DTO 不暴露敏感字段，不直接复用内部 entity。
- 不要重复新增相似接口。新写相似查询、回显、导出或状态变更接口前，先搜索已有 Controller/Feign 模式，能复用或收敛时优先收敛。

## 多租户、数据权限与安全

本项目支持 DATASOURCE / SCHEMA / COLUMN 多种隔离策略。租户上下文和数据权限是正确性的一部分。

- 除非代码路径明确是系统级逻辑，否则不要绕过租户过滤、数据权限、Sa-Token 权限或签名校验。
- 实体统一继承 `Entity<T>` / `SuperEntity<T>`（`com.microservice.framework.commons.entity`），自带 `id`、`tenantId` 及下列审计/删除字段，由 MyBatis-Plus（`FieldFill`）自动填充，不要手动赋值或绕过：
  - `deleted`（逻辑删除，`@TableLogic`）
  - `createTime` / `createBy` / `createName`（列 `create_time` / `create_by` / `create_name`）
  - `lastModifyTime` / `lastModifyBy` / `lastModifyName`（列 `last_modify_time` / `last_modify_by` / `last_modify_name`）
- 涉及 `TenantHelper`、动态数据源（`DynamicDataSourceHandler`、`SchemaSwitchingDataSource`）、`@DSTransactional`、网关请求头透传的改动，先读调用链和至少一个相邻实现。
- 数据权限优先使用项目已有 `@DataScope` / `@DataColumn` 注解模式（`com.microservice.framework.db.mybatisplus.datascope.annotation`）；手写 SQL 时必须确认 alias 与数据权限列一致。
- 动态数据源注册、Schema 创建、SQL 脚本执行、共享连接池都要谨慎处理；数据库或迁移改动必须说明兼容性和回滚注意事项。
- 默认同时考虑 MySQL 和 PostgreSQL 以及多租户隔离策略。使用数据库方言、函数、锁语法或大小写敏感对象名前要说明兼容性。
- 不要输出或提交真实密钥、token、密码、私钥、生产配置、个人信息原文。

## 国际化、字典与展示字段

- 涉及多语言展示时，优先使用项目现有 `@I18nMethod`、`@I18nField`、字典和响应转换模式（`i18n-spring-boot-starter`）。
- 不要在业务 service 中硬编码多语言映射或前端展示文案，除非周边代码已经采用同一模式。
- DTO 增加展示名称、字典文本、语言字段时，检查 `@Schema`、字典类型、i18n 关系、Feign 回显和前端字段期望是否一致。
- 字典和枚举变更要考虑历史数据、导入导出、列表筛选和权限菜单配置。

## 消息、任务与异步

- 消息中间件使用 RocketMQ（如 wms）。异步/MQ 消费者失败模式必须明确：需要重试就抛出异常，需要幂等就先查幂等依据。
- 不要吞掉 MQ 消费、批处理、文件解析、AI/RAG 异步任务、变更日志（diff-log）中的关键异常。
- 高并发或可重复触发操作（出入库、结算、导出、批处理等），优先查现有分布式锁、幂等键和重试模式。
- 变更日志使用 diff-log（`@DiffField`、`DiffLogInterceptor`）现有模式，不要另起一套审计实现。

## 插件模块注意事项

- Workflow：Warm Flow 流程定义、实例状态、任务分配/转办/加签/减签/终止/撤回是核心业务，监听器与消息发送副作用必须显式可读。
- WMS：库存、收货、出库、容器/储位占用、库存流水必须关注事务性和审计性，流水不能只更新最终状态而丢失过程。
- TMS：结算规则和公式计算必须保留计算可追溯性，避免误改人工费用。
- AI：RAG、向量库、Neo4j/Milvus、文档处理通常依赖异步或外部服务，失败模式必须可见。
- Monitor：Spring Boot Admin 监控服务，避免暴露敏感端点与凭证。

## 领域预读清单

进入下列领域前，先读对应核心类和至少一个调用方，避免凭文件名猜行为：

- 动态数据源/租户隔离：`DynamicDataSourceHandler`、`SchemaSwitchingDataSource`、`TenantHelper`、相关 Redis/local 事件监听器。
- Diff Log：`DiffLogInterceptor`、`DefaultDiffItemsToLogContentService`、`@DiffField` 的实体标注方式。
- IAM 登录/权限/消息：认证 strategy、token listener、`UserServiceImpl`、`RoleServiceImpl`、消息模板/通知 strategy。
- WMS 收货/库存：收货计划、入库单、库存流水、容器/储位占用相关 service。
- TMS 结算：`SettleServiceImpl`、`SettleRuleServiceImpl`、`RuleOption`、`RuleSymbol`、`CalculateUtils`。
- AI/RAG：knowledge item/chunk/vectorization service、Neo4j/Milvus factory/store、文档解析和异步任务入口。

## 验证规则

选择与改动风险匹配的检查。

- 文档/注释改动：通常运行 `git diff --check` 即可。
- Java 编译改动：运行聚焦模块编译和相关测试。
- 代码规范治理：优先使用项目已有 Spotless、Checkstyle 或 IDE 检查逐步治理；不要为了清零扫描结果批量格式化无关文件。
- shared framework 改动：可行时编译被改 starter，并至少编译一个下游模块。
- Feign API 契约改动：编译对应 Feign API 模块和至少一个消费方/提供方。
- Gateway 或鉴权改动：编译 gateway，并检查过滤器顺序、白名单、签名、token、租户头透传。
- 数据库、租户、数据权限、动态数据源改动：验证 SQL、租户/权限行为、幂等性和回滚假设。
- MQ/异步任务改动：验证消费失败路径、重试、幂等、日志和至少一个核心处理路径。

如果无法完成验证，必须报告：尝试执行的命令、失败现象、可能原因、对当前改动的风险、建议下一步。

## 框架集成与构建注意（避免下次踩同一坑）

- **Spring Boot 4 拆分了 RabbitAutoConfiguration**：原 `org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration` 不再在 `spring-boot-autoconfigure` 主 jar 内，需要单独的 `spring-boot-amqp` 模块。如果业务没引 `spring-boot-starter-amqp`，直接删掉 `@SpringBootApplication(exclude = RabbitAutoConfiguration.class)` 的 exclude 参数即可，否则会编译失败。
- **Nacos 2.x Open API 参数名是 `tenant` 不是 `namespaceId`**：发布（POST `/v1/cs/configs`）/删除/查询都用 `tenant=<namespaceId>` 字段；`namespaceId` 是 v1 旧端点的参数。用错会把 config 落到 public 命名空间而不是 `v1-dev`。
- **Nacos config `dataId` 必须带扩展名**：后端 `optional:nacos:${spring.application.name}.properties` 拼出的 dataId 是带 `.properties`（或 `.yaml`）的；Nacos 上传的 dataId 必须完全匹配，包括扩展名，否则客户端拿不到（"config data not exist"）。
- **Spring Boot fat jar 的 `application.yml` 是构建时打包进去的**：改源 `src/main/resources/application.yml` 后**必须重新 `mvn package`**，否则启动用的还是旧 jar 里的内嵌 config（fat jar 启动顺序是 jar 内 > classpath > 文件系统）。同理 `FeignConstants` 这种类内常量改完也要 repackage。
- **菜单 component 路径必须跟前端 `microService`（驼峰）对齐**：`sys_resource.component` 字段从老项目的 `/micro-service/...`（横线）改成 `/microService/...`（驼峰）。前端 Vite `import.meta.glob('../views/**/*.vue')` + Vben `normalizeViewPath` 双向 normalize 期望驼峰，否则所有从动态菜单点进去的页面都 fallback 到 not-found.vue（404）。

## 安全边界

没有用户明确批准，不要执行：

- `git commit`、`git push`、删除分支、强推或改写历史。
- 任务范围外的破坏性文件系统操作。
- `DROP`、批量 `DELETE`、Schema 重写等破坏性数据库操作。
- 生产、远程或外部账号操作。
- secret 轮换、权限变更或凭证内容输出。
- 未经明确需求与维护者确认，不要随意调整 `micro-service-platform-framework` 的公共行为；确需调整时按 shared framework 验证规则执行。
- 需要维护者确认的事项统一向仓库所有者提出 issue / PR 讨论，包括：调整 framework 公共行为、新增/合并/拆分服务与模块、新增公共工具类、新增相似接口、引入缓存、平滑迁移方案等。

默认允许在相关任务中执行：

- 读取文件、搜索代码、查看日志、运行本地构建/测试、编辑项目文件、使用 `git status`/`git diff`。

## Agent 回复风格

- 简洁但具体。
- 优先说明改了什么、验证了什么。
- 有帮助时带上文件路径。
- 如实说明不确定性和残余风险。
- 不要掩盖构建或测试失败。

## 良好行为示例

示例：业务规则有歧义。

- 不好：默认允许一个新状态流转，并在所有地方实现它。
- 好：先查当前 enum/service/controller 的用法，推断状态机；如果缺失流转无法从代码确认，再询问用户。

示例：修 Bug。

- 不好：只修表象，然后跳过验证。
- 好：复现或检查失败路径，做最小修复，运行聚焦检查，并说明仍未覆盖的部分。

示例：重构。

- 不好：重命名 helper 并格式化无关模块。
- 好：保持公开契约稳定，只重构被触及逻辑，并尽可能验证行为前后一致。
