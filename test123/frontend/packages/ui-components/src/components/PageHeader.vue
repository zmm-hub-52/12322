<template>
  <header class="page-header">
    <h2 class="page-header__title">{{ title }}</h2>
    <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    <div v-if="$slots.extra" class="page-header__extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 通用页头组件（统一组件示例）。
 *
 * 说明：
 * 1. 样式全部引用 ui-theme 提供的 CSS 变量，保证品牌统一。
 * 2. 后续可将基于 Siemens iX 封装的通用组件（表格/表单/弹窗/搜索栏等）
 *    沉淀到本包，子应用统一 import，避免各子应用重复实现。
 */
defineProps<{
  /** 页标题 */
  title: string;
  /** 副标题（可选） */
  subtitle?: string;
}>();
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--brand-border);
  margin-bottom: 16px;
}
.page-header__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--brand-text);
}
.page-header__subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.page-header__extra {
  margin-left: auto;
}
</style>
