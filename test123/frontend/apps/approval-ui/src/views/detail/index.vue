<template>
  <div class="approval-detail">
    <PageHeader title="审批详情" :subtitle="`单号：${id}`" />
    <p class="hint">这是详情占位页，用于演示「同一子应用多个内部路由」。</p>
    <a class="link" @click="router.push('/approval/list')">← 返回列表</a>
  </div>
</template>

<script setup lang="ts">
/**
 * 审批详情（占位页）
 * 用于演示一个子应用内部存在多个路由（列表 / 详情），
 * 主应用侧通过「唯一实例名 + appName 分组」让它们各自独立 tab。
 */
import { useRoute, useRouter } from 'vue-router';
import { PageHeader } from '@platform/ui-components';

const route = useRoute();
const router = useRouter();
const id = route.params.id ?? '-';
</script>

<style scoped>
.approval-detail {
  color: var(--brand-text);
}
.hint {
  color: var(--brand-text-secondary);
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}
</style>
