# @platform/ui-theme —— 品牌主题包

> 面向**未来接手的开发者 / AI 编码助手**。本文件说明 ui-theme 提供了什么、怎么用、有哪些坑。
> 定位：统一**西门子 iX** 品牌主题的接入、design token、主题切换（含跟随系统）与字体。

---

## 1. 一句话定位

所有应用（主应用 + 各子应用）通过本包接入**同一套西门子 iX 视觉规范**：

- 嵌入无界时跟随主应用主题；
- 独立运行时也能正常展示与切换主题。

**视觉一致性来自：同一套主题资源、design token、字体、图标及使用规范**，而不是组件改名。

---

## 2. 文件结构

```
packages/ui-theme/
├─ src/
│  ├─ theme.css   # 引入 iX 完整样式 + 定义 --brand-* 语义别名
│  ├─ fonts.css   # Siemens Sans @font-face（占位，见 §6）
│  └─ index.ts    # 主题管理 API（themeSwitcher + 跟随系统 + 持久化）
└─ README.md      # 本文件
```

使用方式：

```ts
import '@platform/ui-theme/theme.css';   // 引入 iX 样式 + token 别名
import { applyThemeVariant } from '@platform/ui-theme';
```

---

## 3. iX design token 体系（7 个维度）

iX 的 `--theme-*` token 共 271 个，分 7 个维度。**业务优先直接用 `--theme-*`**，本包不重新复制这些值。

| 维度 | 前缀 | 数量 | 说明 / 示例 |
|---|---|---|---|
| 颜色 | `--theme-color-*` | 125 | `--theme-color-primary`、`--theme-color-std-text`、`--theme-color-soft-bdr`、`--theme-color-alarm`… |
| 图表 | `--theme-chart-*` | 40 | 数据可视化配色（只有做图表才需要） |
| 排版 | `--theme-font-*` / `--theme-line-*` / `--theme-h1~h6` / `--theme-body` / `--theme-text-*` / `--theme-label` / `--theme-display` / `--theme-code` | 50+ | 字号/字重/行高/标题/正文/标签/展示/代码 |
| 间距（modular scale） | `--theme-ms-*` | 9 | `--theme-ms--2`(10px) ~ `--theme-ms-6`(42px)，rem 阶梯，也用作字号 |
| 阴影 | `--theme-shadow-*` | 5 | `--theme-shadow-1` ~ `4` + `--theme-inset-shadow-1` |
| 边框 | `--theme-*-bdr-1/2` | 20+ | 各种颜色 × 粗细，如 `--theme-std-bdr-1`、`--theme-soft-bdr-1` |

**iX 没有的 token**（平台需自行补充，见 §4）：
- **圆角 radius**：iX 无 radius token（圆角在组件内部固化）。
- **solid 页面背景**：iX 只有半透明表面色（`--theme-color-backdrop`、`--theme-color-component-*`），没有 solid 白/黑背景 token。

关键 token 值速查（classic light）：

| token | 值 |
|---|---|
| `--theme-color-primary` | `#006e93` |
| `--theme-color-std-text` | `rgba(0,10,20,.9)` |
| `--theme-color-soft-bdr` | `rgba(0,20,40,.2)` |
| `--theme-font-size-default` | `0.875rem`（14px） |
| `--theme-ms-0` / `--theme-ms-1` | `0.875rem` / `1rem` |

---

## 4. `--brand-*` 语义别名（平台补充层）

本包定义了一组 `--brand-*` 别名，映射到 iX token + 补充 iX 缺失的少量值。**业务可以用 `--brand-*`，也可以直接用 `--theme-*`**（别名只是便捷入口，不是二次封装）。

| 类别 | `--brand-*` | 映射 |
|---|---|---|
| 主色 | `--brand-primary` / `--brand-primary-hover` / `--brand-primary-contrast` | `--theme-color-primary*` |
| 文本 | `--brand-text` / `-secondary` / `-disabled` | `--theme-color-{std,soft,weak}-text` |
| 边框 | `--brand-border` / `--brand-border-strong` | `--theme-color-{soft,std}-bdr` |
| 功能色 | `--brand-success` / `-warning` / `-danger` | `--theme-color-{success,warning,alarm}` |
| 间距 | `--brand-space-1` ~ `-8` | `--theme-ms--2` ~ `--theme-ms-5` |
| 字号 | `--brand-font-xs/sm/md/lg/xl` | `--theme-font-size-*` |
| 阴影 | `--brand-shadow` / `-md` / `-lg` | `--theme-shadow-1/3/4` |
| 圆角 | `--brand-radius-sm` / `--brand-radius` / `--brand-radius-lg` | 平台定义（iX 无） |
| 背景 | `--brand-bg` / `-secondary` / `-tertiary` | 平台定义（iX 无 solid 背景） |

> 命名规范：颜色/文本/边框/背景沿用旧命名；间距用 `--brand-space-N`；字号用 `--brand-font-*`；阴影用 `--brand-shadow-*`。

---

## 5. 主题机制（themeSwitcher + 跟随系统 + 无界同步）

### 5.1 官方 themeSwitcher

主题切换使用 iX 官方 `themeSwitcher`（`@siemens/ix` 导出），**支持 `light | dark | system`（system = 跟随系统）**。

关键 API（本包已在 `index.ts` 封装）：

```ts
import { applyThemeVariant, getCurrentMode, getStoredThemeVariant, storeThemeVariant, themeSwitcher } from '@platform/ui-theme';

applyThemeVariant('system');      // 跟随系统
applyThemeVariant('dark');        // 深色
getCurrentMode();                 // 实际生效 'light' | 'dark'
themeSwitcher.themeChanged.on(({ mode, colorSchema, isMediaChange }) => { ... });
```

### 5.2 无界微前端里的同步

- **主应用**是主题的权威持有者：管理偏好（`system/light/dark`），监听 `themeSwitcher.themeChanged`，把**实际 mode**（light/dark）经无界 `bus` 广播（事件名 `BUS_EVENTS.THEME_CHANGE`）。
- **子应用**监听 bus，收到 mode 后 `applyThemeVariant(mode)`（子应用只跟 mode，不自己判断 system）。
- 主题偏好持久化 key：`platform-theme`（值为 `system|light|dark`）。

### 5.3 主题如何生效

iX 靠 `<html>` 上的两个属性切换：

```html
<html data-ix-theme="classic" data-ix-color-schema="light|dark|system">
```

`applyThemeVariant` 内部调用 `themeSwitcher.setTheme('classic', variant)` 设置这两个属性。

---

## 6. 字体（Siemens Sans）

- iX 5.x 的 npm 包**不含字体文件**，CSS 里只声明了字体栈 `Siemens Sans, Arial, Helvetica, sans-serif`。
- Siemens Sans 是**西门子商业授权字体**，需要拿到授权的 woff2 文件。
- 本包 `src/fonts.css` 提供了 `@font-face` 骨架（已注释），拿到字体文件后：
  1. 放入 `packages/ui-theme/src/assets/fonts/`（或改为 CDN url）；
  2. 取消 `@font-face` 注释并修正文件名。
- **未提供字体文件时**：浏览器回退到 Arial/Helvetica，功能正常，仅品牌字体不生效。

---

## 7. iX 原生 HTML 基础样式（重要）

`siemens-ix.css` 自带对**原生 HTML 元素**的基础样式，以及排版工具类。业务写页面时**优先用这些**，不要自己另造样式：

| 能力 | 用法 |
|---|---|
| 标题 | `<h1>` ~ `<h6>`（全局已带样式），或加类 `.h1` ~ `.h6`、`.typography-h1` ~ `.typography-h6` |
| 正文 | `.typography-body` / `.typography-body-sm` / `.typography-label` 等 |
| 表格 | **`.ix-table`** 类（注意：不是裸 `<table>`，需显式加类） |
| 列表/代码/引用 | `<ul>` / `<code>` / `<pre>` / `<blockquote>` 全局已带样式 |

> iX v5 **没有表格组件**，列表用「原生 `<table class="ix-table">` + iX token」。

---

## 8. 关键坑（务必记住）

1. **`:root` 在无界 shadow DOM 里匹配不到子应用的 `<html>`**：无界子应用渲染在 `<wujie-app>` 的 shadow root 里，`:root` 匹配的是 shadow host（`<wujie-app>`），不是子应用的 `<html>`。所以本包 token 别名**全部用纯属性选择器 `[data-ix-theme='classic']`**，不能用 `:root`。写新样式时同样遵守。
2. **iX 无 radius token**：圆角是组件内部固化的，平台圆角需自行定义（本包已定义 `--brand-radius-*`）。
3. **iX 无 solid 背景 token**：只有半透明表面色，页面 solid 背景需自行定义（本包已定义 `--brand-bg-*`）。
4. **CSS `@import` 注释里别写 `*/` 组合**：注释里写 `.typography-*/.ix-table` 这类带 `*/` 的文本会被 postcss 误判为注释结束。
5. **iX 导出名是 `toast` 不是 `showToast`**：全局 toast 反馈用 `import { toast, showMessage } from '@siemens/ix'`。

---

## 9. 业务代码示例

```vue
<script setup>
import { IxButton, IxInput } from '@platform/ui-components';  // 组件
import { applyThemeVariant } from '@platform/ui-theme';        // 主题
</script>

<template>
  <!-- 用 iX 组件 -->
  <IxButton variant="primary" @click="save">保存</IxButton>
  <IxInput v-model="kw" placeholder="搜索" />

  <!-- 用原生 HTML + iX 工具类 -->
  <h2 class="typography-h2">标题</h2>
  <table class="ix-table">...</table>

  <!-- 用 token（--theme-* 或 --brand-*） -->
  <div style="color: var(--theme-color-std-text)">文本</div>
</template>
```

---

## 10. 维护原则

- **不要二次封装 iX 组件**：直接 re-export（见 `@platform/ui-components`）。
- **不要复制 iX token 值**：直接用 `--theme-*`，或映射到 `--brand-*` 别名。
- 新增平台语义别名时，只加「确实被多个应用复用的」，并同步更新本文档 §4 映射表。
- 升级 iX 版本后，检查 token 是否有变化，同步更新本文档 §3 的速查表。
