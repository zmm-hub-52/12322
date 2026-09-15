import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types'
import { clearToken, getToken, setToken } from '@/utils/token'

/**
 * 统一的 axios 实例与请求封装。
 * - 基础地址使用相对路径 /api，由 Vite 代理转发到后端。
 * - 认证约定（sa-token）：后端在响应头 Authorization 里返回 token，前端存本地；
 *   后续请求统一在 Authorization 头里带回；401 时清 token 并跳登录页。
 */

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：统一注入鉴权信息
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    // 若后端要求 `Bearer <token>` 前缀，改这里即可
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截器：捕获 Authorization 头 + 统一错误处理
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 后端在响应头里带 Authorization（sa-token），前端取出并存储
    const auth = response.headers?.authorization
    if (auth) {
      const token = String(auth).replace(/^Bearer\s+/i, '')
      if (token) setToken(token)
    }
    return response
  },
  (error) => {
    const status: number | undefined = error?.response?.status
    if (status === 401) {
      clearToken()
      // 登录失效：整页跳登录页（用 window.location 避免与 router 产生循环依赖）
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login')
      }
    } else {
      console.error('[request error]', error)
    }
    return Promise.reject(error)
  },
)

/** 泛型请求方法：解包 AxiosResponse，直接返回业务数据 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config).then((res) => res.data as T)
}

export default instance
