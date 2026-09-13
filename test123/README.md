# 报修系统 · 前端底座

基于 **Vue 3 + TypeScript + Vite** 的轻量前端脚手架，UI 采用西门子 **Siemens iX**（Industrial Experience）组件库。作为报修系统（用户提报 → 运维接单 → 处理 → 用户确认 → 关闭）的前端基础，业务页面在此之上开发。

## 技术栈

| 层 | 选型 | 版本策略 |
|---|---|---|
| 框架 | Vue 3 | ^3.5（主流稳定） |
| 构建 | Vite | ^7.3（上一稳定大版本） |
| 语言 | TypeScript | ~5.9（5.x 主流稳定） |
| 路由 | vue-router | ^4.6（4.x 主流稳定） |
| 状态 | Pinia | ^3.0（3.x 主流稳定） |
| 请求 | axios | ^1.8 |
| UI 组件 | @siemens/ix + @siemens/ix-vue | ^5.2.1 |
| 图标 | @siemens/ix-icons | ^3.5.0 |
| 数据网格 | ag-grid-community + @siemens/ix-aggrid | ^35.3 / ^5.1（方案 B 用） |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 类型检查
npm run type-check

# 生产构建
npm run build

# 预览构建产物
npm run preview
```

## 目录结构

```
src/
  api/           # axios 实例与业务接口（对接 Flowable 后端时在此扩展）
    request.ts   #   统一请求封装（token 注入 / 错误处理占位）
  stores/        # Pinia 状态
    app.ts       #   应用级状态（标题、主题）
    auth.ts      #   鉴权与当前用户（角色占位）
  router/        # 路由与守卫
  layouts/       # 布局（ix-application 应用外壳）
  views/         # 页面
  components/    # 业务组件（后续添加）
  types/         # 全局类型
  styles/        # 全局样式
  main.ts        # 入口：注册 iX + 主题 + 挂载
```

## Siemens iX 集成方式（重点）

项目同时使用了 iX 的两种形态，各司其职：

1. **原生 Web Components（`<ix-*>` 标签）**
   - 在 `main.ts` 中通过 `defineCustomElements()` 一次性注册全部 115 个自定义元素。
   - 在 `vite.config.ts` 中配置 `isCustomElement: tag => tag.startsWith('ix-')`，让 Vue 编译器把它们当原生元素处理。
   - 用于**布局 / 结构类**组件（`ix-application`、`ix-menu` 等），因为 Web Component 的具名插槽（slot）只有原生标签才能正确透传。

2. **Vue 类型化封装（`@siemens/ix-vue` 的 PascalCase 组件）**
   - `import { IxButton, IxInput, IxSelect, IxTextarea } from '@siemens/ix-vue'`
   - 用于**表单 / 交互类**组件：基于 Stencil Vue output target 生成，**原生支持 `v-model`**，事件也已正确映射。
   - 命令式反馈 API：`showToast()`、`showMessage.info/warning/error/success()`。

3. **主题**
   - `main.ts` 引入 `@siemens/ix/dist/siemens-ix/siemens-ix.css`（含 classic 主题 + Siemens Sans 字体）。
   - 通过 `data-ix-theme` / `data-ix-color-schema` 属性切换主题（light / dark）。

### 事件注意

- iX 是 Web Components，自定义事件名是 camelCase（如 `valueChange`、`validityStateChange`）。
- 使用 `IxInput` 等 Vue 封装时直接用 `v-model` / `@valueChange`；使用原生 `<ix-input>` 时需手动 `:value` + `@valueChange="x = $event.detail"`。
- `IxButton` 未转发 `clicked` 事件，用原生 `@click` 即可。

## 后端对接（Flowable）

- 开发环境代理已配置：`/api` → `VITE_API_BASE_URL`（默认 `http://localhost:8080`），见 `vite.config.ts` 与 `.env.development`。
- 统一请求封装在 `src/api/request.ts`，业务接口按模块拆分到 `src/api/` 下。
- 报修单状态机（待接单 → 处理中 → 待确认 → 已关闭，含取消/驳回分支）将在业务阶段以显式配置表形式实现。

## 列表方案对比示例

iX v5 移除了内置表格，脚手架里放了两套列表实现做对比（数据为 `src/mocks/orders.ts` 假数据）：

- **方案 A · 原生表格**：`/demo/native`（`src/views/demo/OrderTableNative.vue`）
  原生 `<table>` + iX 周边组件（`ix-select` 筛选、`ix-pill` 状态、`ix-pagination` 分页、`ix-empty-state` 空态）。零额外依赖，体积最小。
- **方案 B · AG Grid**：`/demo/aggrid`（`src/views/demo/OrderTableAgGrid.vue`）
  AG Grid 社区版 + `@siemens/ix-aggrid` 官方主题（`getIxTheme`）。排序/筛选/列宽/分页开箱即用，但引入约 300KB+（gzip）体积。

## 组件清单参考

iX v5 提供 115 个组件，覆盖：应用外壳、按钮、表单（input/select/textarea/checkbox/radio/toggle/date…）、菜单/树/标签页、弹窗/抽屉/提示（modal/toast/message）、卡片/KPI、上传、工作流步骤等。完整列表见 [ix.siemens.io/components/overview](https://ix.siemens.io/docs/components/overview)。

> 注意：iX v5 移除了 `ix-table` / `ix-data-grid` 数据表格，列表/表格需用原生表格 + iX 样式，或引入 AG Grid + `@siemens/ix-aggrid`（见上方两个示例页）。
