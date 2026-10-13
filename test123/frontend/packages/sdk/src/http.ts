import axios, { type AxiosInstance, type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { CreateHttpOptions } from './types';

/**
 * 创建统一的 http 客户端。
 *
 * 职责（薄封装，只做通用能力，不含业务）：
 * 1. 请求拦截：注入 token（每次请求实时取，保证 token 刷新后不失效）
 * 2. 响应拦截：401 统一回调（主应用跳登录，子应用经 bus 通知主应用）
 * 3. 统一超时
 *
 * 注意：token 的唯一持有者是主应用，子应用「禁止自存 token」，
 *       只通过 getToken 回调实时获取。
 */
export function createHttp(options: CreateHttpOptions = {}): AxiosInstance {
  const instance = axios.create({
    baseURL: options.baseURL ?? '',
    timeout: 15000,
  });

  // 请求拦截器：注入 token
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = options.getToken?.();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  // 响应拦截器：统一 401 处理
  instance.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
      if (error.response?.status === 401) {
        options.onUnauthorized?.();
      }
      return Promise.reject(error);
    },
  );

  return instance;
}
