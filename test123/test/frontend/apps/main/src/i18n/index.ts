import { createI18n } from 'vue-i18n';
import zh from './locales/zh';
import en from './locales/en';
import de from './locales/de';

/** 支持的语言 */
export type Locale = 'zh' | 'en' | 'de';

/**
 * 主应用国际化实例。
 * 语言切换由 locale store 负责（改 locale + 经 bus 广播给子应用）。
 */
export const i18n = createI18n({
  legacy: false, // 组合式 API 模式
  locale: 'zh', // 默认中文
  fallbackLocale: 'zh',
  messages: { zh, en, de },
});
