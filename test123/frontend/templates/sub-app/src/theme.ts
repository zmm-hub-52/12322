import { applyTheme, getStoredTheme, type ThemeMode } from '@platform/ui-theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 子应用主题初始化：恢复上次主题 + 监听主应用 bus 广播实时同步。
 */
export function initTheme(): void {
  applyTheme(getStoredTheme());
  window.$wujie?.bus?.$on(BUS_EVENTS.THEME_CHANGE, (theme) => {
    applyTheme(theme as ThemeMode);
  });
}
