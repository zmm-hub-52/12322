# AGENT-VUE.md — AI 编码规范（Vue 3 · TypeScript）

> 目标：结构清晰、组件可复用、**不重复造轮子**、类型安全。
> 技术栈：Vue 3 · TypeScript · Element Plus · Pinia · Vite · vue-router · axios
> 分级：【必须】硬性遵守 ·【推荐】尽量遵守 ·【参考】可选

## 一、总原则（最高优先级）

【必须】禁止造轮子。写通用组件/工具前先确认 Element Plus、VueUse、Pinia、axios、dayjs、lodash-es 是否已提供；已提供则直接用，禁止手写等价实现。
【必须】优先现成：表单/校验/表格/分页/弹窗/消息提示用 Element Plus；工具用 VueUse/lodash-es；日期用 dayjs。
【必须】先读后写：改代码前先理解现有结构与约定，沿用既有模式。
【必须】最小改动：只改需求范围，不顺手重构、不引入无关依赖。
【必须】新增第三方库前，先确认项目是否已有等价依赖。

## 二、目录结构

【必须】按约定组织：

    src/api/         接口请求（按模块拆分，统一走 request 封装）
    src/views/       页面（路由级组件）
    src/components/  通用组件（可复用、无业务耦合）
    src/stores/      Pinia 状态
    src/router/      路由
    src/types/       TS 类型定义
    src/composables/ 组合式函数（use 前缀）
    src/utils/       通用工具（仅放库不提供的）

【必须】views 与 components 分离：业务页面放 views，跨页面复用的才放 components。

## 三、组件与组合式 API

【必须】使用 <script setup lang="ts"> 组合式 API。
【必须】props 用 defineProps 泛型 + 默认值；emits 用 defineEmits 显式声明。
【必须】单一职责：组件只做展示与交互，业务逻辑抽到 composables（useXxx）。
【必须】单组件控制在 300 行内，超长拆子组件或抽 composable。
【必须】composable 放 src/composables/，use 前缀，命名描述用途（useUserData）。
【推荐】import 顺序：类型 → 组件 → composable → 工具；template 根元素带语义化 class。

## 四、TypeScript 规约

【必须】禁止 any（确需使用必须注释说明理由并尽量收窄）。
【必须】接口/数据结构、API 请求响应都定义类型，放 src/types/ 或就近定义。
【必须】props/emits/store/函数返回值显式标注类型，禁止裸 unknown 直接使用。
【推荐】用 const 断言、Readonly、可选属性、可辨识联合；避免不必要的类型断言 as。
【推荐】组合式函数返回类型显式声明。

## 五、状态管理（Pinia）

【必须】跨组件共享状态用 Pinia，禁止手写全局事件总线/全局变量。
【必须】仅共享状态进 store，组件私有状态用 ref/reactive 就地声明。
【必须】store 命名 useXxxStore，统一写法（setup 或 options 二选一）。
【推荐】异步请求放 action，组件不直接改 store 状态（通过 action）。

## 六、命名规约

【必须】组件名/文件名 PascalCase，且多词（禁止单词，如 List → OrderList）。
【必须】变量/函数 camelCase；常量 UPPER_SNAKE_CASE；类型/接口 PascalCase。
【必须】composable useXxx；store useXxxStore；事件 kebab-case；props 声明 camelCase、模板用 kebab-case。
【推荐】变量名见名知意，禁止 data、item、temp 等泛泛命名（除回调单参数）。

## 七、禁止事项（AI 高频翻车点）

【禁止】自己造表格/分页/表单校验/弹窗/消息提示（用 Element Plus）。
【禁止】重复封装 axios（统一用项目 request.ts）。
【禁止】手写防抖/节流/监听器清理/本地存储（用 VueUse）。
【禁止】template 里写复杂逻辑（放 computed/methods）。
【禁止】无必要直接操作 DOM（优先响应式绑定，确需用 ref）。
【禁止】用 any 逃避类型。
【禁止】组件过大、职责不清、一处逻辑复制多份不抽公共。
【禁止】未确认就引入新 UI 库/组件库。
【禁止】在多个组件里重复写同一请求逻辑（抽 composable 或 api 层）。
