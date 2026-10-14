<template>
  <span class="app-logo">
    <span class="app-logo__mark">XXX</span>
    <span v-if="showText" class="app-logo__text">微服务生态底座</span>
  </span>
</template>

<script setup lang="ts">
/**
 * 品牌 Logo（占位）。
 * 目前用「XXX」三字母占位，等拿到真实西门子 logo 后替换成 <img> 即可。
 */
defineProps<{
  /** 是否显示品牌文字（默认显示） */
  showText?: boolean;
}>();
</script>

<style scoped>
.app-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.app-logo__mark {
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--brand-primary);
}
.app-logo__text {
  font-weight: 600;
  color: var(--brand-text);
}
</style>
