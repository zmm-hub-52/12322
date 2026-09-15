import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { CurrentUser, LoginPayload, UserRole } from '@/types'
import { getCurrentUser, loginWithPassword } from '@/api/auth'
import { clearToken, getToken, setToken as saveToken } from '@/utils/token'

/**
 * 鉴权与当前用户状态。
 * - token 本地持久化（见 src/utils/token.ts），由请求拦截器统一写入。
 * - 登录成功后拉取当前用户；路由守卫据此判断登录态。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref<CurrentUser | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed<UserRole | undefined>(() => user.value?.role)

  function setToken(value: string) {
    token.value = value
    if (value) saveToken(value)
    else clearToken()
  }

  function setUser(value: CurrentUser | null) {
    user.value = value
  }

  /** 账号密码登录：成功后拉取当前用户 */
  async function login(payload: LoginPayload) {
    const result = await loginWithPassword(payload)
    // token 优先取响应头（拦截器已写入本地），body 若也返回则兜底
    if (result?.token) setToken(result.token)
    else token.value = getToken()
    await fetchCurrentUser()
  }

  /** 拉取当前登录用户（也用于 AAD 回调后校验登录态） */
  async function fetchCurrentUser(): Promise<CurrentUser> {
    const me = await getCurrentUser()
    user.value = me
    return me
  }

  function logout() {
    setToken('')
    user.value = null
  }

  return { token, user, isLoggedIn, role, setToken, setUser, login, fetchCurrentUser, logout }
})
