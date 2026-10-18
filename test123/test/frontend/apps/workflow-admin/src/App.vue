<template>
  <div class="app-shell">
    <!-- 子应用顶部条：标题 + 用户信息（主应用 props 下发） -->
    <div class="app-bar">
      <span class="app-bar__title">{{ t('app.title') }}</span>
      <span v-if="userName" class="app-bar__user">{{ t('app.currentUser') }}：{{ userName }}</span>
    </div>
    <!-- 页面内容区：flex 占满剩余高度，各页面在内部自行滚动 -->
    <div class="app-content">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * workflow-admin 根组件
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

<style>
/* 全局：确保 100% 高度链（无界 iframe 内 body 高度需显式撑满） */
html,
body,
#app {
  height: 100%;
  margin: 0;
}
</style>

<style scoped>
.app-shell {
  height: 100%;
  box-sizing: border-box;
  padding: 0 16px 16px;
  background: var(--brand-bg);
  color: var(--brand-text);
  display: flex;
  flex-direction: column;
}
.app-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  flex-shrink: 0;
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
/* 页面内容区：确定高度，供子页面 100% 撑满与内部滚动 */
.app-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.app-content > * {
  flex: 1;
  min-height: 0;
}
</style>
