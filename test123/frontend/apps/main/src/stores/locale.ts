import { defineStore } from 'pinia';
import { bus } from 'wujie';
import { BUS_EVENTS } from '@platform/sdk';
import { i18n, type Locale } from '../i18n';

/** 语言偏好持久化 key */
const LOCALE_STORAGE_KEY = 'platform-locale';

/**
 * 语言 store：主应用统一管理语言，切换后经无界 bus 广播给所有保活子应用。
 * 机制与主题 store 一致。
 */
export const useLocaleStore = defineStore('locale', {
  state: () => ({
    locale: ((localStorage.getItem(LOCALE_STORAGE_KEY) as Locale) || 'zh') as Locale,
  }),
  actions: {
    /** 初始化：应用上次保存的语言 */
    init(): void {
      i18n.global.locale.value = this.locale;
    },
    /** 切换语言并广播给子应用 */
    setLocale(locale: Locale): void {
      this.locale = locale;
      localStorage.setItem(LOCALE_STORAGE_KEY, locale);
      i18n.global.locale.value = locale;
      // 经 bus 广播，保活中的子应用监听后同步切换语言
      bus.$emit(BUS_EVENTS.LOCALE_CHANGE, locale);
    },
  },
});
