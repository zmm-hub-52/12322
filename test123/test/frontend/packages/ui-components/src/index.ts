/**
 * @platform/ui-components 通用组件包
 *
 * 定位：子应用统一从这里 import UI 组件，避免到处直接依赖 @siemens/ix-vue。
 *
 * 约定（重要）：
 * - 只 re-export 官方 iX Vue 组件，保留官方名称/属性/事件，不做二次封装；
 * - 只有当出现「验证过的平台共性需求」时，才在本包局部封装组件；
 *   视觉一致性来自统一的主题资源/token/字体/规范，而非组件改名。
 *
 * 注意：iX 是 Web Components，使用前需在应用入口调用
 *   defineCustomElements()（来自 @siemens/ix/loader，注册 <ix-*> 组件）
 *   以及 @siemens/ix-icons/loader 的 defineCustomElements（注册 <ix-icon>，可别名为 defineIxIcons）。
 */
export * from '@siemens/ix-vue';
