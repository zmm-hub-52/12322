/**
 * 全局基础类型定义。
 * 报修业务相关的类型（报修单、状态机等）在进入业务开发阶段后再补充。
 */

/** 系统角色 */
export type UserRole = 'reporter' | 'maintainer' | 'admin'

/** 当前登录用户 */
export interface CurrentUser {
  id: string
  name: string
  role: UserRole
  avatar?: string
}

/** 后端统一响应结构（Flowable 后端对接时按实际返回调整） */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 登录请求参数（账号密码） */
export interface LoginPayload {
  username: string
  password: string
  remember?: boolean
}

/** 登录返回（token 主要从响应头 Authorization 读取，这里兜底 body 返回） */
export interface LoginResult {
  token?: string
  user?: CurrentUser
}
