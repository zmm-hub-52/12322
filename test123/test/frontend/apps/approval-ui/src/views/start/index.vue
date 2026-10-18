<template>
  <div class="start-page">
    <div class="page-head">
      <h2 class="typography-h2">{{ t('start.title') }}</h2>
      <p class="typography-body page-subtitle">{{ t('start.subtitle') }}</p>
    </div>

    <!-- 选择流程 -->
    <div class="panel">
      <div class="row">
        <label class="row__label">{{ t('start.pickFlow') }}</label>
        <IxSelect v-model="pickedDefId" class="row__select">
          <IxSelectItem v-for="d in defs" :key="d.id" :label="`${d.name}（v${d.version}）`" :value="String(d.id)" />
        </IxSelect>
      </div>
      <div v-if="defs.length === 0" class="empty">{{ t('start.noPublished') }}</div>

      <template v-if="pickedDefId">
        <!-- 审批人选择 -->
        <template v-if="approvalNodes.length">
          <h3 class="section-title">{{ t('start.assignees') }}</h3>
          <div v-for="n in approvalNodes" :key="n.nodeKey" class="row">
            <label class="row__label">{{ n.nodeName }}</label>
            <IxSelect v-model="assignees[n.nodeKey]" class="row__select">
              <IxSelectItem v-for="u in users" :key="u.username" :label="`${u.name}（${u.roleName}）`" :value="u.username" />
            </IxSelect>
          </div>
        </template>

        <!-- Excel 操作区 -->
        <h3 class="section-title">{{ t('start.excelTitle') }}</h3>
        <div class="excel-ops">
          <a class="btn-download" :href="templateUrl" :download="`flow-${pickedDefId}-template.xlsx`">
            {{ t('start.downloadTemplate') }}
          </a>
          <label class="btn-upload">
            {{ t('start.uploadExcel') }}
            <input type="file" accept=".xlsx" class="btn-upload__file" @change="onFilePicked" />
          </label>
          <span v-if="uploading" class="hint">{{ t('start.uploading') }}</span>
        </div>

        <!-- 预览表格 -->
        <div v-if="preview" class="preview">
          <div class="preview__head">
            <span>{{ t('start.previewTitle') }}</span>
            <IxButton variant="primary" :disabled="startableRows.length === 0 || starting" @click="startAll">
              {{ t('start.startAll') }}（{{ startableRows.length }}）
            </IxButton>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th style="width: 48px">{{ t('start.colRow') }}</th>
                <th v-for="k in preview.keys" :key="k" class="mono">{{ k }}</th>
                <th style="width: 130px">{{ t('start.colStatus') }}</th>
                <th style="width: 90px">{{ t('start.colAction') }}</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(row, i) in preview.rows" :key="row.rowNo">
                <tr :class="{ 'row--invalid': !row.valid }">
                  <td>{{ row.rowNo - 1 }}</td>
                  <td v-for="k in preview.keys" :key="k">{{ row.data[k] ?? '' }}</td>
                  <td>
                    <span v-if="row.valid" class="tag tag--ok">{{ t('start.valid') }}</span>
                    <span v-else class="tag tag--bad">{{ t('start.invalid') }}</span>
                  </td>
                  <td>
                    <a v-if="row.valid && !row.started" class="link" @click="startRow(i)">{{ t('start.startOne') }}</a>
                    <span v-else-if="row.started" class="tag tag--ok">{{ t('start.startedTag') }}</span>
                    <span v-else class="hint">—</span>
                  </td>
                </tr>
                <tr v-if="!row.valid" class="row--errors">
                  <td colspan="2" class="hint">{{ t('start.errors') }}</td>
                  <td :colspan="(preview.keys.length - 2) + 1">
                    <span class="err-text">{{ row.errors.join('；') }}</span>
                  </td>
                  <td></td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 发起流程页（Excel 导入模式，两步交互）：
 * 选流程 → 选审批人 → 下载模板 → 填 Excel 上传 → 预览校验 → 逐行点「发起」。
 */
import { onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton, IxSelect, IxSelectItem } from '@platform/ui-components';
import {
  excelTemplateUrl,
  fetchGraph,
  fetchPublishedDefs,
  fetchUsers,
  previewExcel,
  startExcelRow,
} from '../../api';
import { currentUsername } from '../../composables/useCurrentUser';
import { getFeedback } from '@platform/sdk';
import type { ImportPreview, ImportRow, PublishedDef, UserView } from '../../types/schema';

const { t } = useI18n();
const router = useRouter();
const { showToast } = getFeedback();

const defs = ref<PublishedDef[]>([]);
const users = ref<UserView[]>([]);
const pickedDefId = ref('');

/** 审批节点（选处理人，所有行共用） */
const approvalNodes = ref<{ nodeKey: string; nodeName: string }[]>([]);
const assignees = reactive<Record<string, string>>({});

/** 预览状态 */
const preview = ref<ImportPreview | null>(null);
const uploading = ref(false);
const starting = ref(false);

const templateUrl = ref('');

onMounted(async () => {
  defs.value = await fetchPublishedDefs();
  users.value = await fetchUsers();
});

watch(pickedDefId, async (v) => {
  preview.value = null;
  if (!v) return;
  const defId = Number(v);
  templateUrl.value = excelTemplateUrl(defId);
  try {
    const graph = await fetchGraph(defId);
    approvalNodes.value = graph.nodes
      .filter((n) => n.nodeType === 'approval')
      .map((n) => ({ nodeKey: n.nodeKey, nodeName: n.nodeName }));
    for (const n of approvalNodes.value) {
      assignees[n.nodeKey] = defaultAssignee(n.nodeName);
    }
  } catch (e) {
    showToast({ message: (e as Error).message });
  }
});

function defaultAssignee(nodeName: string): string {
  const map: Record<string, string> = {
    部门经理: 'manager_li',
    经理: 'manager_li',
    财务: 'finance_wang',
    质量: 'quality_zhao',
    审批: 'manager_li',
  };
  const hit = Object.keys(map).find((k) => nodeName.includes(k));
  return hit ? map[hit] : users.value[0]?.username ?? '';
}

/** 上传 Excel → 预览 */
async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !pickedDefId.value) return;
  uploading.value = true;
  try {
    const result = await previewExcel(Number(pickedDefId.value), file);
    (result.rows as ImportRow[]).forEach((r) => (r.started = false));
    preview.value = result as ImportPreview;
  } catch (e) {
    showToast({ message: (e as Error).message });
  } finally {
    uploading.value = false;
  }
}

/** 可发起的行（校验通过且未发起） */
function startableRows(): ImportRow[] {
  return (preview.value?.rows ?? []).filter((r) => r.valid && !r.started);
}

/** 逐行发起 */
async function startRow(index: number): Promise<void> {
  const row = preview.value?.rows[index] ?? null;
  if (!row || !pickedDefId.value) return;
  await doStart(row);
}

/** 全部发起（校验通过且未发起的行） */
async function startAll(): Promise<void> {
  starting.value = true;
  try {
    for (const row of startableRows()) {
      await doStart(row, false);
    }
  } finally {
    starting.value = false;
  }
}

async function doStart(row: ImportRow, toastOnSuccess = true): Promise<void> {
  const missing = approvalNodes.value.filter((n) => !assignees[n.nodeKey]);
  if (missing.length) {
    showToast({ message: `${t('start.pickAssignees')}：${missing.map((n) => n.nodeName).join('、')}` });
    return;
  }
  const defId = Number(pickedDefId.value);
  const bizNo = `EXL${Date.now().toString(36).toUpperCase()}${row.rowNo}`;
  try {
    await startExcelRow({
      processDefId: defId,
      data: row.data,
      applicant: currentUsername(),
      assignees: { ...assignees },
      bizNo,
      title: defs.value.find((d) => d.id === defId)?.name ?? '',
    });
    row.started = true;
    if (toastOnSuccess) {
      showToast({ message: `${t('start.started')}（${t('start.colRow')} ${row.rowNo - 1}）` });
    }
  } catch (e) {
    showToast({ message: (e as Error).message });
  }
}
</script>

<style scoped>
.start-page {
  color: var(--brand-text);
}
.page-head {
  margin-bottom: 16px;
}
.page-subtitle {
  color: var(--brand-text-secondary);
  margin-top: 4px;
}
.panel {
  max-width: 960px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.row__label {
  width: 140px;
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.row__select {
  flex: 1;
}
.section-title {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--brand-primary);
}
.empty {
  color: var(--brand-text-disabled);
  padding: 24px 0;
  text-align: center;
}
.hint {
  font-size: 12px;
  color: var(--brand-text-secondary);
}

/* Excel 操作 */
.excel-ops {
  display: flex;
  align-items: center;
  gap: 16px;
}
.btn-download {
  padding: 8px 14px;
  border: 1px solid var(--brand-primary);
  border-radius: var(--brand-radius);
  color: var(--brand-primary);
  text-decoration: none;
  cursor: pointer;
  font-size: 14px;
}
.btn-download:hover {
  background: color-mix(in srgb, var(--brand-primary) 8%, var(--brand-bg));
}
.btn-upload {
  padding: 8px 14px;
  border: 1px solid var(--brand-border-strong);
  border-radius: var(--brand-radius);
  background: var(--brand-bg-secondary);
  color: var(--brand-text);
  cursor: pointer;
  font-size: 14px;
}
.btn-upload:hover {
  background: var(--brand-bg-tertiary);
}
.btn-upload__file {
  display: none;
}

/* 预览表格 */
.preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 13px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.table th,
.table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--brand-border);
  text-align: left;
}
.table th {
  color: var(--brand-text-secondary);
  font-weight: 600;
  background: var(--brand-bg-secondary);
}
.mono {
  font-family: monospace;
}
.row--invalid td {
  background: color-mix(in srgb, var(--brand-danger) 6%, var(--brand-bg));
}
.row--errors td {
  border-bottom: none;
  padding-top: 2px;
  padding-bottom: 8px;
}
.err-text {
  color: var(--brand-danger);
  font-size: 12px;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag--ok {
  background: color-mix(in srgb, var(--brand-success) 15%, var(--brand-bg));
  color: var(--brand-success);
}
.tag--bad {
  background: color-mix(in srgb, var(--brand-danger) 12%, var(--brand-bg));
  color: var(--brand-danger);
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}
</style>
