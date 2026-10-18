/**
 * 跨应用通信契约：统一约定 bus 事件名与共享存储 key。
 * 主应用、子应用都从本包引用，避免手写字符串造成口径不一致。
 */

/** 无界 bus 事件名（跨应用通信契约） */
export const BUS_EVENTS = {
  /** 主题切换：主应用 -> 子应用；payload 为 'light' | 'dark' */
  THEME_CHANGE: 'platform:theme-change',
  /** 语言切换：主应用 -> 子应用；payload 为 'zh' | 'en' | 'de' */
  LOCALE_CHANGE: 'platform:locale-change',
  /** token 过期/未授权：子应用 -> 主应用；通知主应用刷新 token 或跳转登录 */
  TOKEN_EXPIRED: 'platform:token-expired',
  /** 用户信息变化：主应用 -> 子应用；payload 为最新用户信息对象 */
  USER_CHANGED: 'platform:user-changed',
} as const;

/** 跨应用共享的存储 key */
export const STORAGE_KEYS = {
  /** token（仅主应用持有，子应用不落盘） */
  TOKEN: 'platform-token',
  /** 用户信息（主应用持有） */
  USER: 'platform-user',
  /** 多 Tab 元数据（sessionStorage，主应用持有） */
  TABS: 'platform-tabs',
  /** 主题偏好 */
  THEME: 'platform-theme',
} as const;
