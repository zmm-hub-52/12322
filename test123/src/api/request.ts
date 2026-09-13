import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types'

/**
 * 统一的 axios 实例与请求封装。
 * - 基础地址使用相对路径 /api，由 Vite 代理转发到后端。
 * - 后续可在此处统一注入 token、处理错误码、弹出提示。
 */

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：统一注入鉴权信息（对接 Flowable 后端时按需启用）
instance.interceptors.request.use((config) => {
  // const token = useAuthStore().token
  // if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：统一解包与错误处理
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 按后端实际约定解包；此处先原样返回
    return response
  },
  (error) => {
    // 统一错误提示，可接入 iX 的 toast/message
    console.error('[request error]', error)
    return Promise.reject(error)
  },
)

/** 泛型请求方法：解包 AxiosResponse，直接返回业务数据 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config).then((res) => res.data as T)
}

export default instance
