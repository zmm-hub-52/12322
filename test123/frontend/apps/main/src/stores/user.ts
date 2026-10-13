import { defineStore } from 'pinia';
import { STORAGE_KEYS, type PlatformUser } from '@platform/sdk';

/**
 * 用户 store：token 与用户信息的「唯一持有者」。
 * 约定：token 只存在于主应用，子应用禁止自存 token，通过跨应用桥实时读取。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: (localStorage.getItem(STORAGE_KEYS.TOKEN) ?? '') as string,
    userInfo: null as PlatformUser | null,
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
    setUserInfo(user: PlatformUser): void {
      this.userInfo = user;
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    },
    /** 清除登录态 */
    clear(): void {
      this.token = '';
      this.userInfo = null;
      localStorage.removeItem(STORAGE_KEYS.TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
    },
    /**
     * 模拟登录（占位）。
     * 真实场景：微软 AAD OIDC 整页跳转登录，后端换 token 后写入；或走账号密码接口。
     */
    mockLogin(name: string): void {
      this.setToken(`mock-token-${Date.now()}`);
      this.setUserInfo({ id: 'u-1001', name, orgName: '设备管理部', roles: ['approver', 'viewer'] });
    },
  },
});
