/**
 * 主应用 props 注入的全局反馈工具
 * 说明：子应用不自行渲染 toast/message（会被 iframe 边界裁剪），
 *       统一调用主应用下发的 showToast / showMessage（iX toast / showMessage 函数引用）。
 */

/** 弹出全局 toast 提示 */
export function toastMsg(message: string): void {
  const props = window.$wujie?.props as Record<string, unknown> | undefined;
  const fn = props?.showToast as ((opts: { message: string }) => void) | undefined;
  fn?.({ message });
}

/** 弹出全局 message 提示（带标题） */
export function showMessageMsg(message: string, title?: string): void {
  const props = window.$wujie?.props as Record<string, unknown> | undefined;
  const fn = props?.showMessage as ((opts: { message: string; title?: string }) => void) | undefined;
  fn?.({ message, title });
}
