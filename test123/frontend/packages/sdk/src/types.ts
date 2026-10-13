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
   * 子应用侧默认从主应用暴露的 window.__PLATFORM__.getToken() 读取。
   */
  getToken?: () => string | null;
  /** 401 未授权回调（主应用跳登录 / 子应用经 bus 通知主应用） */
  onUnauthorized?: () => void;
}
