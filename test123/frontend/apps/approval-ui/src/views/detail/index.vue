<template>
  <div class="approval-detail">
    <h2 class="typography-h2">{{ t('detail.title') }}</h2>
    <p class="typography-body">{{ t('detail.no') }}：{{ id }}</p>
    <p class="hint">{{ t('detail.hint') }}</p>
    <a class="link" @click="router.push('/approval/list')">{{ t('detail.back') }}</a>
  </div>
</template>

<script setup lang="ts">
/**
 * 审批详情（占位页）
 * 用于演示一个子应用内部存在多个路由（列表 / 详情），
 * 主应用侧通过「唯一实例名 + appName 分组」让它们各自独立 tab。
 */
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
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
