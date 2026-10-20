<template>
  <div class="finished-list">
    <div class="page-head">
      <div>
        <h2 class="typography-h2">{{ t('finished.title') }}</h2>
        <p class="typography-body page-subtitle">{{ t('finished.subtitle') }}</p>
      </div>
    </div>

    <!-- 已完结流程表格 -->
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('finished.colBizNo') }}</th>
          <th>{{ t('finished.colTitle') }}</th>
          <th>{{ t('finished.colApplicant') }}</th>
          <th>{{ t('finished.colProcessName') }}</th>
          <th>{{ t('finished.colCreateTime') }}</th>
          <th>{{ t('finished.colEndTime') }}</th>
          <th>{{ t('finished.colStatus') }}</th>
          <th>{{ t('finished.colAction') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in instances" :key="row.id">
          <td class="mono">{{ row.bizNo ?? '—' }}</td>
          <td>{{ row.title ?? '—' }}</td>
          <td>{{ row.applicant ?? '—' }}</td>
          <td>{{ row.processName ?? '—' }}</td>
          <td>{{ formatTime(row.createTime) }}</td>
          <td>{{ formatTime(row.endTime) }}</td>
          <td>
            <span class="tag" :class="row.status === 'finished' ? 'tag--finished' : 'tag--terminated'">
              {{ row.status === 'finished' ? t('finished.statusFinished') : t('finished.statusTerminated') }}
            </span>
          </td>
          <td>
            <!-- 仅正常走完的实例可导出 formData -->
            <a v-if="row.status === 'finished'" class="link" :href="formDataExportUrl(row.id)" :download="`form-data-${row.id}.xlsx`">
              {{ t('finished.download') }}
            </a>
            <span v-else class="hint">—</span>
          </td>
        </tr>
        <tr v-if="instances.length === 0">
          <td colspan="8" class="empty">{{ t('finished.empty') }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * 已完结流程页：展示所有已结束的实例（正常走完 + 驳回终止），
 * 仅「正常走完」的实例可导出 formData 为 Excel。
 */
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { fetchFinishedInstances, formDataExportUrl } from '../../api';
import type { FinishedInstance } from '../../types/schema';

const { t } = useI18n();

const instances = ref<FinishedInstance[]>([]);

onMounted(async () => {
  try {
    instances.value = await fetchFinishedInstances();
  } catch {
    instances.value = [];
  }
});

function formatTime(v: string | null): string {
  if (!v) return '—';
  return v.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.finished-list {
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
.tag--finished {
  background: #e8f5ee;
  color: var(--brand-success);
}
.tag--terminated {
  background: color-mix(in srgb, var(--brand-warning) 15%, var(--brand-bg));
  color: var(--brand-warning);
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
.hint {
  color: var(--brand-text-disabled);
}
.empty {
  text-align: center;
  color: var(--brand-text-disabled);
  padding: 24px 0;
}
</style>
