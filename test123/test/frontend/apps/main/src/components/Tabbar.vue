<template>
  <div class="tabbar">
    <div
      v-for="tab in tabs"
      :key="tab.id"
      class="tabbar__item"
      :class="{ 'is-active': tab.id === activeTabId }"
      @click="emit('activate', tab.id)"
    >
      <span class="tabbar__title">{{ tab.title }}</span>
      <span class="tabbar__close" title="关闭" @click.stop="emit('close', tab.id)">×</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 多 Tab 栏：展示打开的所有 tab，支持切换与关闭。
 * 状态由父组件（DefaultLayout + useTabs）提供，本组件只负责展示与事件。
 */
import type { TabMeta } from '@platform/tab-manager';

defineProps<{
  tabs: readonly TabMeta[];
  activeTabId: string | null;
}>();

const emit = defineEmits<{
  activate: [id: string];
  close: [id: string];
}>();
</script>

<style scoped>
.tabbar {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 36px;
  padding: 0 8px;
  background: var(--brand-bg-secondary);
  border-bottom: 1px solid var(--brand-border);
  overflow-x: auto;
}
.tabbar__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  height: 28px;
  border: 1px solid var(--brand-border);
  border-bottom: none;
  border-radius: var(--brand-radius) var(--brand-radius) 0 0;
  background: var(--brand-bg);
  color: var(--brand-text-secondary);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.tabbar__item.is-active {
  color: var(--brand-primary);
  border-color: var(--brand-primary);
  font-weight: 600;
}
.tabbar__close {
  color: var(--brand-text-disabled);
  font-size: 15px;
  line-height: 1;
}
.tabbar__close:hover {
  color: var(--brand-danger);
}
</style>
