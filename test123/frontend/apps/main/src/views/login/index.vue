<template>
  <div class="login">
    <div class="login__card">
      <h2>登录</h2>
      <p class="hint">占位登录页：真实场景接微软 AAD OIDC 或账号密码接口</p>
      <input v-model="name" class="field" type="text" placeholder="请输入姓名" />
      <button class="btn" @click="onLogin">模拟登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页（占位）。
 * 真实场景：微软 AAD 为默认登录方式（整页跳转 OIDC，后端换 token）；
 * 账号密码接口 /api/auth/login 为占位。这里仅做「模拟登录」演示登录态流转。
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../../stores/user';

const router = useRouter();
const userStore = useUserStore();
const name = ref('张三');

function onLogin(): void {
  userStore.mockLogin(name.value || '张三');
  router.push('/');
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
</style>
