/**
 * 主应用认证 API：对接 workflow-service 轻量用户体系（测试身份切换）。
 */

import { createHttp } from '@platform/sdk';

/** 后端服务地址（dev 直连；可用 VITE_API_BASE_URL 覆盖） */
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090';

const http = createHttp({ baseURL: API_BASE });

/** 用户视图（与后端 UserViewDto 对齐） */
export interface AuthUser {
  id: number;
  username: string;
  name: string;
  roleCode: string;
  roleName: string;
}

/** 解包后端 Result{code,message,data} */
async function unwrap<T>(p: Promise<{ data: { code: number; message: string; data: T } }>): Promise<T> {
  const res = await p;
  const body = res.data;
  if (body.code !== 0) {
    throw new Error(body.message || '接口调用失败');
  }
  return body.data;
}

/** 用户列表（登录页/顶栏切换下拉） */
export async function fetchUsers(): Promise<AuthUser[]> {
  return unwrap<AuthUser[]>(http.get('/api/auth/users'));
}

/** 登录（测试用途免密，按用户名） */
export async function loginByUsername(username: string): Promise<AuthUser> {
  return unwrap<AuthUser>(http.post('/api/auth/login', { username }));
}
