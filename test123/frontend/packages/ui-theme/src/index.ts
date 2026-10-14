/**
 * @platform/ui-theme
 * 品牌主题助手。样式变量见同目录 theme.css（需在入口显式 import）。
 * 用法：`import '@platform/ui-theme/theme.css'` + `import { applyTheme } from '@platform/ui-theme'`
 */

/** 主题模式 */
export type ThemeMode = 'light' | 'dark';

/** 主题持久化的 localStorage key（与主应用主题 store 保持一致） */
export const THEME_STORAGE_KEY = 'platform-theme';

/**
 * 应用主题：在 document.documentElement（<html>）上设置 iX 主题属性。
 * - data-ix-theme="classic"：西门子 classic 品牌主题
 * - data-ix-color-schema="light|dark"：明/暗色系（iX 的 --theme-* 变量随它自动切换）
 * @param theme 目标主题 'light' | 'dark'
 */
export function applyTheme(theme: ThemeMode): void {
  const root = document.documentElement;
  root.setAttribute('data-ix-theme', 'classic');
  root.setAttribute('data-ix-color-schema', theme);
}

/** 读取当前页面实际生效的主题（缺省视为 light） */
export function getCurrentTheme(): ThemeMode {
  return document.documentElement.getAttribute('data-ix-color-schema') === 'dark' ? 'dark' : 'light';
}

/** 从 localStorage 读取上次保存的主题偏好，默认 light */
export function getStoredTheme(): ThemeMode {
  return localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
}

/** 保存主题偏好到 localStorage（下次启动恢复） */
export function storeTheme(theme: ThemeMode): void {
  localStorage.setItem(THEME_STORAGE_KEY, theme);
}
