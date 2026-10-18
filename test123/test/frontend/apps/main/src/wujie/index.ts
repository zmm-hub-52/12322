import { bus } from 'wujie';
import { useUserStore } from '../stores/user';
import { BUS_EVENTS } from '@platform/sdk';

/**
 * 主应用跨应用事件初始化。
 *
 * 实时数据（token/用户/主题）不再通过 window 全局桥暴露，
 * 而是经 props 传函数下发给子应用（见 useTabs 的 props 工厂），
 * 子应用每次调用 props.getToken() 即可拿到最新值。
 * 这里只负责监听子应用上报的事件。
 */
export function initPlatformEvents(): void {
  const userStore = useUserStore();

  // 监听子应用上报的 token 过期事件，统一处理（刷新 token 或跳登录）
  bus.$on(BUS_EVENTS.TOKEN_EXPIRED, () => {
    // 占位：真实场景在此刷新 token，或清除登录态并跳转登录页
    userStore.clear();
    window.location.href = '/login';
  });
}
