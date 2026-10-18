# 子应用脚手架模板

新子应用从这里复制起步。**使用步骤**：

1. 复制整个目录到 `apps/` 下，重命名（如 `apps/inventory`）。
2. 改 `package.json` 的 `name`（如 `@platform/inventory`）。
3. 改 `vite.config.ts` 的端口（主应用 5173、审批 5174，新子应用顺延 5175、5176…）。
4. 改 `index.html` 的 `<title>`。
5. 在 `src/router/index.ts` 里定义本子应用的路由。
6. 在主应用 `src/config/menu.ts` 的 `appRegistry` 注册 base url，`menus` 加菜单项。

## 约定（务必遵守）

- 默认 iframe 隔离，生命周期改造已内置（`__WUJIE_MOUNT/__WUJIE_UNMOUNT`）。
- 主题：引入 `@platform/ui-theme/theme.css`，用 CSS 变量，不写死颜色。
- 请求：用 `@platform/sdk` 的 `createHttp`，token 从 `window.parent.__PLATFORM__.getToken()` 实时取，**禁止自存 token**。
- 组件：优先用 `@platform/ui-components` 的封装组件。
- 用户信息：从 `window.$wujie.props.user` 读取（快照），实时数据用跨应用桥。
