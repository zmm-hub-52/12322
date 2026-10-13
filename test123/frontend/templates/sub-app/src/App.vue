<template>
  <div class="app-shell">
    <router-view />
  </div>
</template>

<script setup lang="ts">
/** 子应用根组件（模板） */
</script>

<style scoped>
.app-shell {
  min-height: 100%;
  background: var(--brand-bg);
  color: var(--brand-text);
}
</style>
