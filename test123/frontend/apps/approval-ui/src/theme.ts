import { applyTheme, getStoredTheme, type ThemeMode } from '@platform/ui-theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 初始化子应用主题：
 * 1. 先从 localStorage 读取上次主题偏好（与主应用保持一致，避免首屏闪变）
 * 2. 监听主应用经无界 bus 广播的主题切换事件，实时同步切换
 */
export function initTheme(): void {
  // 初始主题
  applyTheme(getStoredTheme());

  // 无界环境下监听主应用广播（bus 事件契约见 @platform/sdk 的 BUS_EVENTS）
  window.$wujie?.bus?.$on(BUS_EVENTS.THEME_CHANGE, (theme) => {
    applyTheme(theme as ThemeMode);
  });
}
