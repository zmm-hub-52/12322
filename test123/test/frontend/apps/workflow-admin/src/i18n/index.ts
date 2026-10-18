import { createI18n } from 'vue-i18n';
import { BUS_EVENTS } from '@platform/sdk';
import zh from './locales/zh';
import en from './locales/en';
import de from './locales/de';

/** 支持的语言 */
export type Locale = 'zh' | 'en' | 'de';

/** 子应用国际化实例 */
export const i18n = createI18n({
  legacy: false,
  locale: 'zh',
  fallbackLocale: 'zh',
  messages: { zh, en, de },
});

/**
 * 初始化子应用语言：
 * 1. 从 localStorage 读取上次语言偏好（与主应用保持一致，避免首屏闪变）
 * 2. 监听主应用经无界 bus 广播的语言切换，实时同步
 */
export function initLocale(): void {
  const saved = localStorage.getItem('platform-locale');
  if (saved === 'en' || saved === 'de') {
    i18n.global.locale.value = saved;
  }
  window.$wujie?.bus?.$on(BUS_EVENTS.LOCALE_CHANGE, (locale) => {
    i18n.global.locale.value = locale as Locale;
  });
}
