/**
 * @platform/ui-components 通用组件包
 *
 * 定位：子应用统一从这里 import UI 组件，避免到处直接依赖 @siemens/ix-vue。
 *
 * 内容：
 * 1. re-export 西门子 iX 的 Vue 组件（PascalCase，如 IxButton/IxInput/IxApplication）；
 * 2. 项目自定义的封装组件（如 PageHeader）。
 *
 * 注意：iX 是 Web Components，使用前需在应用入口调用
 *   defineCustomElements()（来自 @siemens/ix/loader，注册 <ix-*> 组件）
 *   以及 @siemens/ix-icons/loader 的 defineCustomElements（注册 <ix-icon>，可别名为 defineIxIcons）。
 */
export * from '@siemens/ix-vue';
export { default as PageHeader } from './components/PageHeader.vue';
