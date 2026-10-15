/** 用户信息（主应用通过 props 下发给子应用的静态快照） */
export interface PlatformUser {
  /** 用户唯一标识 */
  id: string;
  /** 用户姓名 */
  name: string;
  /** 所属机构/部门名称 */
  orgName?: string;
  /** 角色标识列表 */
  roles?: string[];
}

/** 创建 http 客户端的选项 */
export interface CreateHttpOptions {
  /** 网关地址 / API 前缀（如 '/api' 或完整域名） */
  baseURL?: string;
  /**
   * 获取 token 的函数。每次请求都会实时调用，保证 token 刷新后不失效。
   * 子应用侧默认从主应用经 props 下传的 getToken 函数实时读取。
   */
  getToken?: () => string | null;
  /** 401 未授权回调（主应用跳登录 / 子应用经 bus 通知主应用） */
  onUnauthorized?: () => void;
}

/**
 * 主应用经 props 下发给子应用的契约。
 * 数据（如 user）是快照；函数（getToken/getUser/getTheme/jump）是活引用，
 * 子应用每次调用都能拿到最新值（默认 iframe 同域下函数可跨应用传递）。
 */
export interface PlatformProps {
  /** 用户信息快照（静态展示用） */
  user?: PlatformUser | null;
  /** 实时获取 token（函数引用） */
  getToken?: () => string | null;
  /** 实时获取用户信息 */
  getUser?: () => PlatformUser | null;
  /** 实时获取当前主题 */
  getTheme?: () => 'light' | 'dark';
  /** 跨应用跳转：跳转到主应用某个路由 */
  jump?: (path: string) => void;
  /**
   * 全局反馈：主应用下发 showToast / showMessage。
   * 子应用调用后由主应用统一弹（覆盖全屏，不被 iframe 边界裁剪），
   * 保证全局反馈位置/样式一致。
   */
  showToast?: (...args: any[]) => unknown;
  showMessage?: (...args: any[]) => unknown;
}
