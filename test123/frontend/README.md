# 微服务生态底座 · 前端（无界微前端）

基于 **无界（wujie）** 的微前端工程，主应用壳 + 子应用 + 公共包的单仓（pnpm workspace）结构。

## 目录结构

```
frontend/
├─ apps/
│  ├─ main/          主应用壳：菜单/路由/多 Tab/鉴权/用户信息/全局状态/主题
│  └─ approval-ui/   子应用：审批中心（查询列表页示例）
├─ packages/
│  ├─ sdk/           公共请求封装（axios）+ 跨应用通信契约常量
│  ├─ ui-theme/      统一品牌主题（CSS 变量，light/dark）
│  ├─ ui-components/ 基于 iX 的通用组件封装（占位）
│  └─ tab-manager/   多 Tab 管理：保活 + 10 分钟超时销毁 + sessionStorage 持久化
└─ templates/
   └─ sub-app/       子应用脚手架模板（新子应用从这里复制起步）
```

## 快速开始

```bash
pnpm install          # 安装依赖
pnpm dev              # 同时启动主应用(5173) + 子应用(5174)
pnpm build            # 构建主应用 + 子应用
```

单独启动：

```bash
pnpm dev:main         # 主应用 http://localhost:5173
pnpm dev:approval     # 子应用 http://localhost:5174
```

## 开发约定

- 子应用默认 iframe 隔离，保活模式 `alive: true`，10 分钟未激活自动销毁。
- 每个 Tab 一个独立无界实例（唯一 `name`），`appName` 用于逻辑子应用分组。
- 跨应用通信走无界 `bus`（事件契约见 `@platform/sdk` 的 `BUS_EVENTS`）。
- 主题统一由 `@platform/ui-theme` 提供 CSS 变量，主应用切换后经 bus 广播，子应用同步。
