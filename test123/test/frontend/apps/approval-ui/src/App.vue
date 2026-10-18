<template>
  <div class="app-shell">
    <!-- 子应用顶部条：展示从主应用 props 下发的用户信息（静态快照） -->
    <div class="app-bar">
      <span class="app-bar__title">{{ t('app.title') }}</span>
      <span v-if="userName" class="app-bar__user">{{ t('app.currentUser') }}：{{ userName }}</span>
    </div>
    <router-view />
  </div>
</template>

<script setup lang="ts">
/**
 * 子应用根组件
 * 说明：用户信息从主应用 props 下发（window.$wujie.props.user），
 *       子应用不自存 token、不自行拉取用户信息。
 */
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

// 从无界注入的 props 中读取用户信息快照
const userName = computed(() => {
  const props = window.$wujie?.props as Record<string, unknown> | undefined;
  const user = props?.user as { name?: string } | undefined;
  return user?.name ?? '';
});
</script>

<style scoped>
.app-shell {
  min-height: 100%;
  padding: 0 16px 16px;
  background: var(--brand-bg);
  color: var(--brand-text);
}
.app-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  border-bottom: 1px solid var(--brand-border);
  margin-bottom: 8px;
}
.app-bar__title {
  font-weight: 600;
  color: var(--brand-primary);
}
.app-bar__user {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
</style>
