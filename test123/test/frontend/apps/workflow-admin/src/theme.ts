import {
  applyThemeVariant,
  getCurrentSystemAppearance,
  getStoredThemeVariant,
  type ThemeMode,
} from '@platform/ui-theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 初始化子应用主题：
 * 1. 首屏先读持久化偏好（与主应用一致）；偏好为 system 时用系统外观，避免首屏闪变
 * 2. 监听主应用经无界 bus 广播的实际 mode（light/dark），实时同步切换
 */
export function initTheme(): void {
  const variant = getStoredThemeVariant();
  const initialMode: ThemeMode = variant === 'system' ? getCurrentSystemAppearance() : variant;
  applyThemeVariant(initialMode);

  window.$wujie?.bus?.$on(BUS_EVENTS.THEME_CHANGE, (mode) => {
    applyThemeVariant(mode as ThemeMode);
  });
}
