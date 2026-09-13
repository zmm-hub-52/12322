import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { CurrentUser, UserRole } from '@/types'

/**
 * 鉴权与当前用户状态。
 * 骨架阶段用内存态占位，后续对接 Flowable 后端的登录/权限。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const user = ref<CurrentUser | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed<UserRole | undefined>(() => user.value?.role)

  function setToken(value: string) {
    token.value = value
  }

  function setUser(value: CurrentUser | null) {
    user.value = value
  }

  function logout() {
    token.value = ''
    user.value = null
  }

  return { token, user, isLoggedIn, role, setToken, setUser, logout }
})
