import { bus } from 'wujie';
import { useUserStore } from '../stores/user';
import { useThemeStore } from '../stores/theme';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 跨应用桥初始化。
 *
 * 主应用将 token/用户/主题 读取函数暴露在 window.__PLATFORM__ 上，
 * 子应用（同域 iframe）通过 window.parent.__PLATFORM__ 实时读取。
 *
 * 为什么用 window 暴露而不是 props 快照：
 * - props 是 startApp 时的一次性快照，token/用户变化后子应用拿不到新值；
 * - 同域 iframe 可直接访问 window.parent，实时性最好。
 */
export function initPlatformBridge(): void {
  const userStore = useUserStore();
  const themeStore = useThemeStore();

  window.__PLATFORM__ = {
    getToken: () => userStore.token || null,
    getUser: () => userStore.userInfo,
    getTheme: () => themeStore.mode,
  };

  // 监听子应用上报的 token 过期事件，统一处理（刷新 token 或跳登录）
  bus.$on(BUS_EVENTS.TOKEN_EXPIRED, () => {
    // 占位：真实场景在此刷新 token，或清除登录态并跳转登录页
    userStore.clear();
    window.location.href = '/login';
  });
}
