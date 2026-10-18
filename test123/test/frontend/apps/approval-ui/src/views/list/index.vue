<template>
  <div class="approval-list">
    <div class="page-head">
      <div>
        <h2 class="typography-h2">{{ t('list.title') }}</h2>
        <p class="typography-body page-subtitle">
          {{ t('list.subtitle') }}：{{ userName }}
        </p>
      </div>
      <IxButton variant="primary" @click="goStart">{{ t('list.startFlow') }}</IxButton>
    </div>

    <!-- 待办表格 -->
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('list.colBizNo') }}</th>
          <th>{{ t('list.colTitle') }}</th>
          <th>{{ t('list.colNode') }}</th>
          <th>{{ t('list.colTime') }}</th>
          <th>{{ t('list.colAction') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.taskId">
          <td class="mono">{{ task.bizNo ?? '—' }}</td>
          <td>{{ task.title ?? '—' }}</td>
          <td>
            <span class="tag" :class="task.nodeType === 'approval' ? 'tag--approval' : 'tag--fill'">
              {{ task.nodeName }}
            </span>
          </td>
          <td>{{ formatTime(task.createTime) }}</td>
          <td>
            <a class="link" @click="goDetail(task.instanceId)">{{ t('list.handle') }}</a>
          </td>
        </tr>
        <tr v-if="tasks.length === 0">
          <td colspan="5" class="empty">{{ t('list.empty') }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的待办：当前登录用户的引擎任务列表（GET /api/task/my）。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton } from '@platform/ui-components';
import { fetchMyTasks } from '../../api';
import { currentUsername, getCurrentUser } from '../../composables/useCurrentUser';
import type { TaskView } from '../../types/schema';

const { t } = useI18n();
const router = useRouter();

const tasks = ref<TaskView[]>([]);
const userName = getCurrentUser().name ?? '';

onMounted(async () => {
  try {
    tasks.value = await fetchMyTasks(currentUsername());
  } catch {
    tasks.value = [];
  }
});

function goStart(): void {
  router.push('/approval/start');
}

function goDetail(instanceId: number): void {
  router.push(`/approval/detail/${instanceId}`);
}

function formatTime(v: string | null): string {
  if (!v) return '—';
  return v.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.approval-list {
  color: var(--brand-text);
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-subtitle {
  color: var(--brand-text-secondary);
  margin-top: 4px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.table th,
.table td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--brand-border);
  text-align: left;
}
.table th {
  color: var(--brand-text-secondary);
  font-weight: 600;
}
.mono {
  font-family: monospace;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag--fill {
  background: color-mix(in srgb, var(--brand-primary) 12%, var(--brand-bg));
  color: var(--brand-primary);
}
.tag--approval {
  background: color-mix(in srgb, var(--brand-warning) 15%, var(--brand-bg));
  color: var(--brand-warning);
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}
.empty {
  text-align: center;
  color: var(--brand-text-disabled);
  padding: 24px 0;
}
</style>
