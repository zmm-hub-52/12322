<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/**
 * AAD 授权回调占位页。
 * 后端 OAuth 成功后 302 跳回这里，通过 /api/user/me 校验登录态；
 * 响应头里的 Authorization（sa-token）由拦截器自动存取。
 */
onMounted(async () => {
  try {
    await authStore.fetchCurrentUser()
    router.replace('/home')
  } catch {
    router.replace('/login')
  }
})
</script>

<template>
  <div class="callback-page">
    <ix-spinner />
    <p>正在完成登录…</p>
  </div>
</template>

<style scoped>
.callback-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: var(--theme-color-soft-text, #666);
}
</style>
