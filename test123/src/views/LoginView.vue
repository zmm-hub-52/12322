<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { IxButton, IxCheckbox, IxInput, showMessage } from '@siemens/ix-vue'
import AppLogo from '@/components/AppLogo.vue'
import MicrosoftIcon from '@/components/MicrosoftIcon.vue'
import { oauthEntryUrl } from '@/api/auth'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()

const showOther = ref(false)
const username = ref('')
const password = ref('')
const remember = ref(true)
const submitting = ref(false)

const isDark = computed(() => appStore.theme === 'dark')

/** 微软 AAD 登录：整页跳转到后端授权入口（占位 URL）。 */
function onMicrosoftLogin() {
  window.location.href = oauthEntryUrl
}

/** 账号密码登录 */
async function onPasswordLogin() {
  if (!username.value.trim() || !password.value) {
    showMessage.warning('提示', '请输入账号和密码', '知道了')
    return
  }
  submitting.value = true
  try {
    await authStore.login({
      username: username.value.trim(),
      password: password.value,
      remember: remember.value,
    })
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.replace(redirect)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function onForgot() {
  showMessage.info('提示', '忘记密码为占位功能，请联系管理员重置', '知道了')
}

/** xxx 登录（占位） */
function onOtherProvider() {
  showMessage.info('提示', 'xxx 登录为占位入口，待接入', '知道了')
}
</script>

<template>
  <div class="login-page">
    <header class="login-topbar">
      <div class="brand">
        <AppLogo :size="32" />
        <span class="brand-name">报修系统</span>
      </div>

      <button
        class="theme-toggle"
        type="button"
        :title="isDark ? '切换到浅色' : '切换到暗色'"
        :aria-label="isDark ? '切换到浅色' : '切换到暗色'"
        @click="appStore.toggleTheme()"
      >
        <ix-icon :name="isDark ? 'sun' : 'moon'" size="16" />
      </button>
    </header>

    <main class="login-main">
      <section class="login-brand">
        <h1 class="brand-title">报修系统</h1>
        <p class="brand-sub">运维工单管理平台</p>
        <span class="brand-accent"></span>
      </section>

      <section class="login-panel">
        <div class="login-card">
          <h2 class="card-title">登录</h2>
          <p class="card-sub">欢迎使用，请选择登录方式</p>

          <!-- 默认：微软 AAD -->
          <template v-if="!showOther">
            <button class="microsoft-btn" type="button" @click="onMicrosoftLogin">
              <MicrosoftIcon :size="20" />
              <span>使用 Microsoft 账号登录</span>
            </button>

            <button class="other-toggle" type="button" @click="showOther = true">
              <span>其他登录方式</span>
              <span class="chev">
                <ix-icon name="chevron-down-small" size="16" />
              </span>
            </button>
          </template>

          <!-- 其他登录方式（隐藏微软登录，二者互斥） -->
          <template v-else>
            <button class="back-link" type="button" @click="showOther = false">
              <ix-icon name="chevron-left-small" size="16" />
              <span>返回 Microsoft 登录</span>
            </button>

            <div class="other-heading">账号密码登录</div>

            <div class="field">
              <IxInput v-model="username" label="账号" placeholder="请输入账号" />
            </div>
            <div class="field">
              <IxInput v-model="password" label="密码" type="password" placeholder="请输入密码" />
            </div>

            <div class="form-row">
              <IxCheckbox v-model="remember">记住我</IxCheckbox>
              <a class="link" href="#" @click.prevent="onForgot">忘记密码？</a>
            </div>

            <IxButton class="login-btn" variant="primary" :disabled="submitting" @click="onPasswordLogin">
              {{ submitting ? '登录中…' : '登录' }}
            </IxButton>

            <div class="divider"><span>或</span></div>

            <button class="provider-btn" type="button" @click="onOtherProvider">
              xxx 登录
            </button>
          </template>

          <footer class="card-footer">
            <span>版本 v0.1.0</span>
            <span>·</span>
            <a class="link" href="#" @click.prevent>帮助</a>
          </footer>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--theme-color-2, #eff0f1);
  color: var(--theme-color-std-text, #111);
  transition: background-color 0.2s ease, color 0.2s ease;
}

.login-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-name {
  font-weight: 600;
  font-size: 1.05rem;
  color: var(--theme-color-std-text, #111);
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 999px;
  border: 1px solid var(--theme-color-soft-bdr, #ddd);
  background: var(--theme-color-1, #fff);
  color: var(--theme-color-std-text, #111);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.theme-toggle:hover {
  background: var(--theme-color-2, #f0f0f0);
}

.login-main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 3rem;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 4rem 4rem 6rem;
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
}

.brand-title {
  margin: 0;
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--theme-color-std-text, #111);
}

.brand-sub {
  margin: 0;
  font-size: 1.15rem;
  color: var(--theme-color-soft-text, #666);
}

.brand-accent {
  display: inline-block;
  width: 3rem;
  height: 4px;
  border-radius: 2px;
  background: var(--theme-color-primary, #006e93);
  margin-top: 0.25rem;
}

.login-panel {
  display: flex;
  justify-content: center;
}

.login-card {
  width: 100%;
  max-width: 27rem;
  background: var(--theme-color-1, #fff);
  border: 1px solid var(--theme-color-weak-bdr, #e5e5e5);
  border-radius: 12px;
  padding: 2.5rem 2.25rem;
  box-shadow: var(--theme-shadow-4, 0 12px 24px rgba(0, 0, 0, 0.08));
}

.card-title {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-color-std-text, #111);
}

.card-sub {
  margin: 0 0 2rem;
  font-size: 0.9rem;
  color: var(--theme-color-soft-text, #666);
}

/* 微软登录按钮：微软蓝 */
.microsoft-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  width: 100%;
  height: 3.25rem;
  padding: 0 1rem;
  border-radius: 8px;
  border: 1px solid #0067b8;
  background: #0067b8;
  color: #ffffff;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.microsoft-btn:hover {
  background: #005a9e;
  border-color: #005a9e;
}

.other-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  width: 100%;
  margin-top: 1.25rem;
  padding: 0.75rem;
  border: none;
  background: transparent;
  color: var(--theme-color-primary, #006e93);
  font-size: 0.9rem;
  cursor: pointer;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0;
  margin-bottom: 1.25rem;
  border: none;
  background: transparent;
  color: var(--theme-color-primary, #006e93);
  font-size: 0.9rem;
  cursor: pointer;
}

.back-link:hover {
  text-decoration: underline;
}

.chev {
  display: inline-flex;
  transition: transform 0.2s ease;
}

.other-heading {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--theme-color-soft-text, #666);
  margin-bottom: 0.5rem;
}

.field {
  margin-bottom: 0.75rem;
}

.field :deep(ix-input) {
  width: 100%;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0.25rem 0 1rem;
}

.link {
  color: var(--theme-color-primary, #006e93);
  text-decoration: none;
  font-size: 0.85rem;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.login-btn {
  width: 100%;
  height: 3.25rem;
}

.divider {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 1rem 0;
  color: var(--theme-color-weak-text, #999);
  font-size: 0.8rem;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--theme-color-weak-bdr, #e5e5e5);
}

.provider-btn {
  width: 100%;
  padding: 0.7rem 1rem;
  border-radius: 8px;
  border: 1px solid var(--theme-color-soft-bdr, #ddd);
  background: transparent;
  color: var(--theme-color-std-text, #111);
  font-size: 0.9rem;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.provider-btn:hover {
  background: var(--theme-color-2, #f0f0f0);
}

.card-footer {
  margin-top: 2rem;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  color: var(--theme-color-weak-text, #999);
}

@media (max-width: 900px) {
  .login-main {
    grid-template-columns: 1fr;
    gap: 2rem;
    padding: 2rem 1.5rem 3rem;
  }

  .login-brand {
    align-items: center;
    text-align: center;
  }
}
</style>
