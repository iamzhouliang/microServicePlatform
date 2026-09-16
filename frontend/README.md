## 安装使用

- 获取项目代码

```bash
git clone <your-git-repo-url>
```

- 安装依赖

```bash
cd microService-platform-ui
# 如果没有 pnpm 请先安装 pnpm
# npm install -g pnpm
pnpm install

```

- 运行

```bash
# 如果您已运行了配套后端, 请将 vite.config.ts 中的 proxy target 改为后端地址
pnpm run dev:antd
```

- 代码提交

```bash
git add .
# 可以参考下面文件,提交格式
# microService-platform-ui/internal/lint-configs/commitlint-config/index.mjs
git commit -m '提交内容'
# 如果未过 eslint 可以通过下面命令进行修复
npx eslint --fix
```

- 打包

```bash
pnpm build
```