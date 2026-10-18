<template>
  <div class="login">
    <div class="login__card">
      <h2>{{ t('login.title') }}</h2>
      <p class="hint">{{ t('login.hint') }}</p>
      <select v-model="picked" class="field">
        <option v-for="u in users" :key="u.username" :value="u.username">
          {{ u.name }}（{{ u.roleName }}）
        </option>
      </select>
      <button class="btn" :disabled="!picked || loading" @click="onLogin">
        {{ t('login.submit') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页（v2：对接后端轻量用户体系）。
 * 下拉选择测试用户（免密）登录，用于流程测试时切换身份。
 * 真实场景可再扩展：微软 AAD OIDC 整页跳转等。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { useUserStore } from '../../stores/user';
import { fetchUsers, type AuthUser } from '../../api/auth';

const { t } = useI18n();
const router = useRouter();
const userStore = useUserStore();

const users = ref<AuthUser[]>([]);
const picked = ref('');
const loading = ref(false);

onMounted(async () => {
  try {
    users.value = await fetchUsers();
    if (users.value.length) picked.value = users.value[0].username;
  } catch {
    /* 后端未就绪：空列表 */
  }
});

async function onLogin(): Promise<void> {
  if (!picked.value || loading.value) return;
  loading.value = true;
  try {
    await userStore.login(picked.value);
    router.push('/');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-bg-secondary);
}
.login__card {
  width: 320px;
  padding: 32px;
  background: var(--brand-bg);
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  box-shadow: var(--brand-shadow);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.login__card h2 {
  margin: 0;
  color: var(--brand-primary);
}
.hint {
  font-size: 12px;
  color: var(--brand-text-secondary);
  margin: 0;
}
.field {
  padding: 8px 10px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  color: var(--brand-text);
}
.btn {
  padding: 8px;
  border: none;
  border-radius: var(--brand-radius);
  background: var(--brand-primary);
  color: #fff;
  cursor: pointer;
}
.btn:hover {
  background: var(--brand-primary-hover);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
