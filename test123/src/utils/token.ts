/**
 * token 本地存取工具。
 * 后端约定（sa-token）：认证成功后，响应头 Authorization 里返回 token，
 * 前端取出并本地存储；后续请求再通过 Authorization 头带回（见 src/api/request.ts）。
 */

const TOKEN_KEY = 'repair-system-token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(value: string): void {
  localStorage.setItem(TOKEN_KEY, value)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
