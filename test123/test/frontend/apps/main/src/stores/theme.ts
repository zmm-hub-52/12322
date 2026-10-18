import { defineStore } from 'pinia';
import { bus } from 'wujie';
import {
  applyThemeVariant,
  getCurrentMode,
  getStoredThemeVariant,
  storeThemeVariant,
  themeSwitcher,
  type ThemeMode,
  type ThemeVariant,
} from '@platform/ui-theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 主题 store：主应用统一管理主题（light / dark / system 跟随系统），
 * 切换后（含「跟随系统」时系统模式变化）经无界 bus 广播实际 mode 给所有子应用。
 * 主题 token 唯一来源是 @platform/ui-theme。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    /** 实际生效的模式（light/dark） */
    mode: 'light' as ThemeMode,
    /** 用户偏好（light/dark/system） */
    variant: getStoredThemeVariant(),
  }),
  actions: {
    /** 初始化：应用偏好 + 监听主题变化（手动切换 / 系统模式变化）广播 */
    init(): void {
      applyThemeVariant(this.variant);
      this.mode = getCurrentMode();
      // 官方 themeSwitcher 会在手动切换、以及「跟随系统」时系统模式变化时触发此事件
      themeSwitcher.themeChanged.on(({ mode, colorSchema }) => {
        this.mode = mode;
        // 手动选 light/dark 才持久化；system 不覆盖持久化偏好
        if (colorSchema === 'light' || colorSchema === 'dark') {
          storeThemeVariant(colorSchema);
          this.variant = colorSchema;
        }
        bus.$emit(BUS_EVENTS.THEME_CHANGE, mode);
      });
    },
    /** 设置主题偏好（light/dark/system）并持久化 */
    setVariant(variant: ThemeVariant): void {
      this.variant = variant;
      storeThemeVariant(variant);
      applyThemeVariant(variant);
    },
  },
});
