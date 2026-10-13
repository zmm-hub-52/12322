import { defineStore } from 'pinia';
import { bus } from 'wujie';
import { applyTheme, getStoredTheme, storeTheme, type ThemeMode } from '@platform/ui-theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 主题 store：主应用统一管理主题，切换后经无界 bus 广播给所有保活子应用。
 * 主题变量唯一来源是 @platform/ui-theme 的 theme.css。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: getStoredTheme() as ThemeMode,
  }),
  actions: {
    /** 初始化：应用上次保存的主题 */
    init(): void {
      applyTheme(this.mode);
    },
    /** 切换主题并广播给子应用 */
    toggle(): void {
      this.setMode(this.mode === 'light' ? 'dark' : 'light');
    },
    /** 设置指定主题并广播 */
    setMode(mode: ThemeMode): void {
      this.mode = mode;
      applyTheme(mode);
      storeTheme(mode);
      // 经 bus 广播，保活中的子应用监听后同步切换 data-theme
      bus.$emit(BUS_EVENTS.THEME_CHANGE, mode);
    },
  },
});
