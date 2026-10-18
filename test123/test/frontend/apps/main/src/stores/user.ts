import { defineStore } from 'pinia';
import { STORAGE_KEYS } from '@platform/sdk';
import { loginByUsername, type AuthUser } from '../api/auth';

/**
 * 用户 store：token 与用户信息的「唯一持有者」。
 * 约定：token 只存在于主应用，子应用禁止自存 token，通过跨应用桥实时读取。
 * v2：对接后端轻量用户体系，免密按用户名登录（测试流程身份切换）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: (localStorage.getItem(STORAGE_KEYS.TOKEN) ?? '') as string,
    userInfo: (() => {
      try {
        const raw = localStorage.getItem(STORAGE_KEYS.USER);
        if (!raw) return null;
        const user = JSON.parse(raw) as AuthUser;
        // 兼容：旧版 mock 用户结构（无 username）视为无效登录态，清掉强制重新登录
        if (!user || !user.username) {
          localStorage.removeItem(STORAGE_KEYS.USER);
          localStorage.removeItem(STORAGE_KEYS.TOKEN);
          return null;
        }
        return user;
      } catch {
        return null;
      }
    })(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    /** 写入 token 并落盘（主应用唯一入口） */
    setToken(token: string): void {
      this.token = token;
      localStorage.setItem(STORAGE_KEYS.TOKEN, token);
    },
    /** 写入用户信息并落盘 */
    setUserInfo(user: AuthUser): void {
      this.userInfo = user;
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    },
    /**
     * 登录（免密，按用户名）：调后端认证接口拿用户信息。
     * 测试流程时顶栏切换用户也走这里（换身份）。
     */
    async login(username: string): Promise<AuthUser> {
      const user = await loginByUsername(username);
      this.setToken(`platform-token-${user.username}`);
      this.setUserInfo(user);
      return user;
    },
    /** 清除登录态 */
    clear(): void {
      this.token = '';
      this.userInfo = null;
      localStorage.removeItem(STORAGE_KEYS.TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
    },
  },
});
