/**
 * 当前登录用户（主应用经 props 注入；getUser() 为活引用）。
 */

export interface CurrentUser {
  id?: number;
  username?: string;
  name?: string;
  roleCode?: string;
  roleName?: string;
}

/** 获取当前用户（优先 getUser()，回退 props.user 快照） */
export function getCurrentUser(): CurrentUser {
  const props = window.$wujie?.props as
    | {
        user?: CurrentUser;
        getUser?: () => CurrentUser | null;
      }
    | undefined;
  return props?.getUser?.() ?? props?.user ?? {};
}

/** 当前用户 username（待办查询/发起人） */
export function currentUsername(): string {
  return getCurrentUser().username ?? '';
}
