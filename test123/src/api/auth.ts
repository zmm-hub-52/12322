import { request } from './request'
import type { CurrentUser, LoginPayload, LoginResult } from '@/types'

/**
 * 认证相关接口（占位，路径待后端确认）。
 * 后端已集成 sa-token oauth2 微软 AAD：Spring Security 自动拿 code 换用户信息，
 * sa-token 本地 login 后，在响应头 Authorization 返回 token（由拦截器统一存取）。
 */

/** 微软 AAD 授权入口 URL（占位）。整页跳转到此地址即可触发 OAuth 流程。 */
export const oauthEntryUrl = '/api/auth/aad/login'

/** 账号密码登录 */
export function loginWithPassword(payload: LoginPayload): Promise<LoginResult> {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'POST',
    data: payload,
  })
}

/** 获取当前登录用户（登录后校验/拉取用户信息用） */
export function getCurrentUser(): Promise<CurrentUser> {
  return request<CurrentUser>({
    url: '/user/me',
    method: 'GET',
  })
}
