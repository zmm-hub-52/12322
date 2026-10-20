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
        <!-- 发起方式切换 -->
        <div class="row">
          <label class="row__label">{{ t('start.mode') }}</label>
          <IxToggle
            :checked="mode === 'form'"
            :text-on="t('start.modeForm')"
            :text-off="t('start.modeExcel')"
            @checked-change="mode = $event.detail ? 'form' : 'excel'"
          />
        </div>

        <!-- 下一任务处理人（动态节点需选；固定节点自动分配不展示） -->
        <template v-if="handlerNodes.length">
          <h3 class="section-title">{{ t('start.assignees') }}</h3>
          <div v-for="n in handlerNodes" :key="n.nodeKey" class="row">
            <label class="row__label">{{ n.nodeName }}</label>
            <IxSelect v-model="assignees[n.nodeKey]" class="row__select">
              <IxSelectItem v-for="u in users" :key="u.username" :label="`${u.name}（${u.roleName}）`" :value="u.username" />
            </IxSelect>
          </div>
        </template>

        <!-- 动态表单模式 -->
        <template v-if="mode === 'form'">
          <h3 class="section-title">{{ t('start.formTitle') }}</h3>
          <div v-if="startFields.length" class="form-box">
            <FormRenderer :ref="(el) => setFormRef(el)" v-model="formData" :fields="startFields" />
          </div>
          <p v-else class="empty">{{ t('start.formEmpty') }}</p>
          <div>
            <IxButton variant="primary" :disabled="starting" @click="startForm">
              {{ t('start.startOne') }}
            </IxButton>
          </div>
        </template>

        <!-- Excel 导入模式 -->
        <template v-else>
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
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 发起流程页（两种模式）：
 * - 动态表单：渲染 start 节点字段，填写后单条发起；
 * - Excel 导入：下载模板 → 填 Excel 上传 → 预览校验 → 逐行发起。
 */
import { onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton, IxSelect, IxSelectItem, IxToggle } from '@platform/ui-components';
import FormRenderer from '../../components/FormRenderer.vue';
import {
  excelTemplateUrl,
  fetchDefSchema,
  fetchGraph,
  fetchPublishedDefs,
  fetchUsers,
  previewExcel,
  startExcelRow,
  startInstance,
} from '../../api';
import { currentUsername } from '../../composables/useCurrentUser';
import { getFeedback } from '@platform/sdk';
import type {
  GraphView,
  ImportPreview,
  ImportRow,
  ProcessNodeView,
  PublishedDef,
  SchemaField,
  UserView,
} from '../../types/schema';

const { t } = useI18n();
const router = useRouter();
const { showToast } = getFeedback();

const defs = ref<PublishedDef[]>([]);
const users = ref<UserView[]>([]);
const pickedDefId = ref('');

/** 发起方式：form 动态表单 / excel 导入 */
const mode = ref<'form' | 'excel'>('form');

/** 发起表单（start 节点字段） */
const startFields = ref<SchemaField[]>([]);
const formData = ref<Record<string, unknown>>({});
let formRef: InstanceType<typeof FormRenderer> | null = null;

/** 发起后的第一个用户任务（串行 1 个，并行多支；需选处理人的动态节点） */
const handlerNodes = ref<ProcessNodeView[]>([]);
const assignees = reactive<Record<string, string>>({});

/** 预览状态 */
const preview = ref<ImportPreview | null>(null);
const uploading = ref(false);
const starting = ref(false);

const templateUrl = ref('');

function setFormRef(el: unknown): void {
  formRef = (el as InstanceType<typeof FormRenderer>) ?? null;
}

onMounted(async () => {
  defs.value = await fetchPublishedDefs();
  users.value = await fetchUsers();
});

watch(pickedDefId, async (v) => {
  preview.value = null;
  formData.value = {};
  if (!v) return;
  const defId = Number(v);
  templateUrl.value = excelTemplateUrl(defId);
  try {
    const [graph, schema] = await Promise.all([fetchGraph(defId), fetchDefSchema(defId)]);
    // 只收集「发起后的第一个用户任务」，且仅动态节点需选处理人（固定节点自动分配）
    handlerNodes.value = firstUserTasks(graph.nodes, graph.links).filter((n) => n.assigneeMode === 'dynamic');
    for (const k of Object.keys(assignees)) delete assignees[k];
    // start 节点字段作为发起表单
    const startNode = graph.nodes.find((n) => n.nodeType === 'start');
    const startKey = startNode?.nodeKey ?? 'start';
    startFields.value = (schema.nodes[startKey] as SchemaField[]) ?? [];
  } catch (e) {
    showToast({ message: (e as Error).message });
  }
});

/** BFS：从 start 出发，跨网关收集「第一层」用户任务（fill/approval） */
function firstUserTasks(nodes: GraphView['nodes'], links: GraphView['links']): ProcessNodeView[] {
  const start = nodes.find((n) => n.nodeType === 'start');
  if (!start) return [];
  const byKey = new Map(nodes.map((n) => [n.nodeKey, n]));
  const outLinks = new Map<string, string[]>();
  for (const l of links) {
    const arr = outLinks.get(l.fromKey) ?? [];
    arr.push(l.toKey);
    outLinks.set(l.fromKey, arr);
  }
  const result: ProcessNodeView[] = [];
  const queue = [start.nodeKey];
  const visited = new Set<string>();
  while (queue.length) {
    const cur = queue.shift()!;
    if (visited.has(cur)) continue;
    visited.add(cur);
    for (const toKey of outLinks.get(cur) ?? []) {
      const next = byKey.get(toKey);
      if (!next) continue;
      if (next.nodeType === 'fill' || next.nodeType === 'approval') {
        if (!result.some((r) => r.nodeKey === next.nodeKey)) result.push(next);
      } else if (
        next.nodeType === 'parallelSplit' ||
        next.nodeType === 'exclusiveSplit' ||
        next.nodeType === 'parallelJoin' ||
        next.nodeType === 'exclusiveJoin'
      ) {
        queue.push(next.nodeKey);
      }
    }
  }
  return result;
}

/** 动态表单发起（单条） */
async function startForm(): Promise<void> {
  if (!pickedDefId.value) return;
  const missing = handlerNodes.value.filter((n) => !assignees[n.nodeKey]);
  if (missing.length) {
    showToast({ message: `${t('start.pickAssignees')}：${missing.map((n) => n.nodeName).join('、')}` });
    return;
  }
  if (formRef && !formRef.validate()) return;
  const defId = Number(pickedDefId.value);
  starting.value = true;
  try {
    await startInstance({
      processDefId: defId,
      applicant: currentUsername(),
      bizNo: `F${Date.now().toString(36).toUpperCase()}`,
      title: defs.value.find((d) => d.id === defId)?.name ?? '',
      formData: { ...formData.value },
      assignees: { ...assignees },
    });
    showToast({ message: t('start.started') });
    // 留在本页：清空表单可继续发起
    formData.value = {};
  } catch (e) {
    showToast({ message: (e as Error).message });
  } finally {
    starting.value = false;
  }
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
  const missing = handlerNodes.value.filter((n) => !assignees[n.nodeKey]);
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
.form-box {
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  padding: 14px;
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
