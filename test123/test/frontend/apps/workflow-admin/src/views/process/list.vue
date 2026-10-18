<template>
  <div class="process-list">
    <div class="page-head">
      <div>
        <h2 class="typography-h2">{{ t('processList.title') }}</h2>
        <p class="typography-body page-subtitle">{{ t('processList.subtitle') }}</p>
      </div>
      <IxButton variant="primary" @click="goCreate">{{ t('processList.newProcess') }}</IxButton>
    </div>

    <!-- 流程表格（iX v5 无表格组件，用原生 table + 品牌变量） -->
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('processList.colCode') }}</th>
          <th>{{ t('processList.colName') }}</th>
          <th>{{ t('processList.colDesc') }}</th>
          <th>{{ t('processList.colStatus') }}</th>
          <th>{{ t('processList.colVersion') }}</th>
          <th>{{ t('processList.colAction') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in processes" :key="p.id">
          <td class="mono">{{ p.defCode }}</td>
          <td>{{ p.defName }}</td>
          <td class="desc">{{ p.description }}</td>
          <td>
            <span class="tag" :class="p.published ? 'tag--published' : 'tag--draft'">
              {{ p.published ? `${t('processList.statusPublished')} v${p.version}` : t('processList.statusDraft') }}
            </span>
          </td>
          <td>{{ p.version }}</td>
          <td class="actions">
            <a class="link" @click="goEdit(p)">{{ t('processList.edit') }}</a>
            <a class="link" @click="goForm(p)">{{ t('processList.formConfig') }}</a>
            <a class="link" @click="onPublish(p)">{{ t('processList.publish') }}</a>
          </td>
        </tr>
        <tr v-if="processes.length === 0">
          <td colspan="6" class="empty">{{ t('processList.empty') }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * 流程配置列表页
 * 说明：新建/编辑/表单配置/发布的统一入口（对应后端 GET/POST/PUT /api/process、POST /api/process/{id}/publish）。
 *      编辑已有流程默认从向导第 3 步（节点链微调）继续，前两步回填可回跳。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton } from '@platform/ui-components';
import { fetchProcessList, publishProcess } from '../../mock/service';
import { toastMsg } from '../../utils/feedback';
import type { ProcessDef } from '../../types/schema';

const { t } = useI18n();
const router = useRouter();

const processes = ref<ProcessDef[]>([]);

onMounted(async () => {
  processes.value = await fetchProcessList();
});

/** 新建流程：从向导第 1 步开始 */
function goCreate(): void {
  router.push('/workflow-admin/process/wizard?mode=new');
}

/** 编辑流程：进向导，默认停在第 3 步（前两步回填可回跳） */
function goEdit(p: ProcessDef): void {
  router.push(`/workflow-admin/process/wizard?mode=edit&id=${p.id}`);
}

/** 直接进入表单配置页（默认选中第一个节点） */
function goForm(p: ProcessDef): void {
  router.push(`/workflow-admin/form?processId=${p.id}`);
}

/** 发布流程（版本 +1，生成快照） */
async function onPublish(p: ProcessDef): Promise<void> {
  const res = await publishProcess(p);
  if (res.ok) {
    toastMsg(t('wizard.published', { version: res.version }));
    processes.value = await fetchProcessList();
  }
}
</script>

<style scoped>
.process-list {
  color: var(--brand-text);
  height: 100%;
  overflow: auto;
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
.desc {
  color: var(--brand-text-secondary);
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
.tag--draft {
  background: var(--brand-bg-tertiary);
  color: var(--brand-text-secondary);
}
.tag--published {
  background: #e8f5ee;
  color: var(--brand-success);
}
.actions {
  white-space: nowrap;
}
.actions .link {
  margin-right: 12px;
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
