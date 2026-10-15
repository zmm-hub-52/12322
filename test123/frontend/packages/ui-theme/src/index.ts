/**
 * @platform/ui-theme —— 品牌主题包入口
 *
 * 职责：统一西门子 iX 主题的接入、切换（含跟随系统）与持久化。
 * 详细说明见同目录 README.md。
 *
 * 用法：
 *   import '@platform/ui-theme/theme.css';  // 引入 iX 样式 + 语义别名
 *   import { applyThemeVariant, getCurrentMode } from '@platform/ui-theme';
 */
import {
  themeSwitcher,
  getCurrentSystemAppearance,
  type ThemeMode,
  type ThemeVariant,
} from '@siemens/ix';

// 重新导出官方 API，方便业务统一从本包导入
export type { ThemeMode, ThemeVariant };
export { themeSwitcher, getCurrentSystemAppearance };

/** 主题偏好持久化 key */
export const THEME_STORAGE_KEY = 'platform-theme';

/**
 * 读取持久化的主题偏好（variant），默认 'system'（跟随系统）。
 */
export function getStoredThemeVariant(): ThemeVariant {
  const v = localStorage.getItem(THEME_STORAGE_KEY);
  return v === 'light' || v === 'dark' || v === 'system' ? v : 'system';
}

/** 持久化主题偏好 */
export function storeThemeVariant(variant: ThemeVariant): void {
  localStorage.setItem(THEME_STORAGE_KEY, variant);
}

/**
 * 应用主题：设置西门子 classic 品牌主题 + 指定配色。
 * @param variant 'light' | 'dark' | 'system'（system=跟随系统，由官方 themeSwitcher 监听系统偏好）
 */
export function applyThemeVariant(variant: ThemeVariant): void {
  themeSwitcher.setTheme('classic', variant);
}

/** 当前实际生效的模式（light/dark；system 会被解析成实际值） */
export function getCurrentMode(): ThemeMode {
  return themeSwitcher.getMode();
}
