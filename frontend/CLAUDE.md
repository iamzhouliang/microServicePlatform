# CLAUDE.md

Claude Code 加载顺序：先读 [AGENTS.md](./AGENTS.md)（规则唯一权威），再读本文件（Claude 高频踩坑与本仓库特例）。两者冲突以 AGENTS.md 为准。

## 频繁踩坑的高频差异

- **依赖管理**：Node `>=20.12.0`、`pnpm@10+`；`pnpm-workspace.yaml` 是 Turbo + pnpm monorepo；`pnpm install` 失败先看是否锁文件 vs `node_modules` 不一致。
- **dev 启动慢**：Vite 7 在 Node 24 + Windows 上首次 ready 大约 28 秒；不要看到没输出就判定挂死，必须等 `VITE v7.2.2 ready in` 才算成。
- **dev 端口可能不是 5173**：Windows 远程会话残留会让 5173 处于 TIME_WAIT，Vite 自动避开选 5666；先看启动日志里的 `Local:` 行确认实际端口，不要硬编码 5173。
- **类型检查**：`pnpm -F @vben/web-antd run typecheck`（仅 web-antd）；跨 package 改动用 `pnpm run check:type`。
- **不覆盖**：`pnpm-lock.yaml`、`internal/*/dist`、`scripts/*/dist` 都是 stub 或锁文件；不要重写、不要删（除非显式任务）。

## 本仓库特例（跟 AGENTS.md 不同或 AGENTS.md 没写）

- **品牌字样**：代码常量 `MICROSERVICE`（全大写）、文档描述 `Microservice Platform`、文件路径 `microService`（小驼峰）。
- **菜单 component 路径**：`sys_resource.component` 必须用 `/microService/...`（驼峰）匹配 Vite `import.meta.glob` 的 pageMap；老格式 `/micro-service/...`（带横线）会全 404。
- **请求客户端**：业务页统一 `requestClient` / `defHttp` / `baseRequestClient`（`apps/web-antd/src/api/request.ts`），不新建裸 `axios`。
- **FastCrud 按钮权限**：用 `apps/web-antd/src/plugin/fast-crud/setup-fast-crud-permission.ts` 的 `permission.prefix` / `prefix:action`。
- **菜单/路由**：来自后端 `preferences.app.accessMode = 'backend'`；菜单访问 `apps/web-antd/src/api/core/menu.ts#getAllMenusApi`（`/iam/resources/router`）。

## 操作前必读

- 改 FastCrud 页面：先读同目录 `index.vue` + `crud.tsx` + `api.ts`，再动手。
- 改权限：后端菜单权限码、`permission.prefix`、路由守卫、操作按钮四端同步检查。
- 改路由 / API 契约：字段、枚举、状态码、权限码是前后端契约，先查后端 DTO 与相似页面。