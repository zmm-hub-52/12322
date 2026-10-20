<template>
  <div class="form-config">
    <!-- 头部 -->
    <div class="page-head">
      <div>
        <a class="link" @click="goBack">
          ← {{ from === 'wizard' ? t('form.backToWizard') : t('form.backToList') }}
        </a>
        <h2 class="typography-h2 page-title">{{ t('form.title') }}</h2>
        <p class="typography-body page-subtitle">{{ t('form.subtitle') }}</p>
      </div>
      <!-- 保存=整份草稿落盘（store 内所有节点字段），不随当前节点类型置灰 -->
      <IxButton variant="primary" :disabled="saving || !nodes.length" @click="save">
        {{ t('form.save') }}
      </IxButton>
    </div>

    <!-- 菜单直达（未指定流程且非向导草稿）：先选择流程 -->
    <div v-if="!processId && from !== 'wizard'" class="flow-picker">
      <label class="flow-picker__label">{{ t('form.selectFlow') }}</label>
      <IxSelect v-model="pickedFlowId" class="flow-picker__select">
        <IxSelectItem v-for="f in flowOptions" :key="f.id" :label="`${f.defName}（${f.defCode}）`" :value="String(f.id)" />
      </IxSelect>
    </div>

    <!-- 已选流程但无图：空态 -->
    <p v-if="processId && !nodes.length" class="empty">{{ t('form.noProcess') }}</p>

    <template v-else>
      <!-- 上卡片：流程节点（横向 SVG 流程图，点选高亮；限高防挤压下卡片） -->
      <div class="card card--flow">
        <div class="card__title">
          {{ t('form.flowCard') }}
          <span class="card__sub">（{{ defName }}）</span>
        </div>
        <FlowGraph
          :nodes="nodes"
          :links="links"
          :selected-key="selectedKey"
          :configured-keys="configuredKeys"
          @select="selectNode"
        />
      </div>

      <!-- 下卡片：节点字段配置 -->
      <div class="card card--grow">
        <div class="card__title">{{ t('form.fieldCard') }}</div>

        <!-- 网关/结束节点：无需表单 -->
        <div v-if="isNonFormNode" class="approval-hint">
          {{ t('form.noFormHint') }}
        </div>

        <div v-else class="field-area">
          <!-- 左：字段库（维度 -> 分类 -> 字段 三级浏览） -->
          <div class="field-lib">
            <div class="col-title">
              {{ t('form.fieldLib') }}
              <span class="col-title__hint">{{ t('form.fieldLibHint') }}</span>
            </div>
            <div class="field-lib__filters">
              <IxSelect v-model="libDimension" class="field-lib__filter">
                <IxSelectItem v-for="d in fieldDimensions" :key="d.id" :label="d.dimensionName" :value="String(d.id)" />
              </IxSelect>
              <IxSelect v-model="libCategory" class="field-lib__filter">
                <IxSelectItem :label="t('form.allCategory')" value="all" />
                <IxSelectItem v-for="c in fieldCategories" :key="c.id" :label="c.categoryName" :value="String(c.id)" />
              </IxSelect>
            </div>
            <div class="field-lib__list">
              <div
                v-for="def in groupedLibrary"
                :key="def.fieldKey"
                class="lib-row"
                :class="{ 'lib-row--added': isAdded(def.fieldKey) }"
              >
                <span class="lib-row__name">{{ def.fieldName }}</span>
                <span class="tag tag--type mono">{{ def.fieldType }}</span>
                <span class="lib-row__spacer"></span>
                <IxButton
                  variant="secondary"
                  class="lib-row__btn"
                  :disabled="isAdded(def.fieldKey)"
                  @click="addField(def)"
                >
                  +
                </IxButton>
              </div>
              <p v-if="!groupedLibrary.length" class="empty">{{ t('form.selectedEmpty') }}</p>
            </div>
          </div>

          <!-- 右：已选字段 -->
          <div class="field-selected">
            <div class="col-title">
              {{ t('form.selectedFields', { node: selectedNode?.nodeName ?? '' }) }}
            </div>
            <p v-if="!nodeFields.length" class="empty">{{ t('form.selectedEmpty') }}</p>
            <div v-for="(f, i) in nodeFields" :key="f.fieldKey" class="sel-block">
              <div class="sel-row">
                <span class="sel-row__index">{{ i + 1 }}</span>
                <span class="sel-row__name">{{ f.fieldName }}</span>
                <span class="tag tag--type mono">{{ f.fieldType }}</span>
                <IxSelect v-model="f.mode" class="sel-row__mode">
                  <IxSelectItem :label="t('form.modeEditable')" value="editable" />
                  <IxSelectItem :label="t('form.modeReadonly')" value="readonly" />
                  <IxSelectItem :label="t('form.modeHidden')" value="hidden" />
                </IxSelect>
                <IxToggle :checked="f.required" @checked-change="f.required = $event.detail" class="sel-row__req" />
                <span class="sel-row__req-label">{{ t('form.required') }}</span>
                <span class="sel-row__spacer"></span>
                <IxButton variant="secondary" :disabled="i === 0" @click="moveField(i, -1)">
                  {{ t('form.moveUp') }}
                </IxButton>
                <IxButton variant="secondary" :disabled="i === nodeFields.length - 1" @click="moveField(i, 1)">
                  {{ t('form.moveDown') }}
                </IxButton>
                <IxButton variant="secondary" @click="removeField(i)">{{ t('form.remove') }}</IxButton>
                <IxButton variant="secondary" @click="toggleExpand(f.fieldKey)">
                  {{ expandedKey === f.fieldKey ? t('form.hideSettings') : t('form.settings') }}
                </IxButton>
              </div>

              <!-- 展开：字段规则 / 提示文案 -->
              <div v-if="expandedKey === f.fieldKey" class="sel-detail">
                <div class="sel-detail__block">
                  <RuleEditor v-model="f.rule" :fields="ruleFields(f.fieldKey)" />
                </div>
                <div class="sel-detail__row">
                  <span class="sel-detail__label">Placeholder</span>
                  <IxInput v-model="f.placeholder" class="sel-detail__input" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 动态表单配置页（上下两卡片）
 * 上卡片：横向 SVG 流程图（点击节点高亮切换）
 * 下卡片：左「字段库」+ 右「已选字段」行内设置（展示方式/必填/取值规则/校验规则）
 *
 * 入口：
 * 1. 流程列表页「表单配置」→ 默认选中第一个节点；
 * 2. 流程向导第 4 步 → 带 nodeId + from=wizard（新建流程经 sessionStorage 传临时草稿结构）。
 * mock 阶段：保存写入 mock service 内存缓存，向导完成度实时可见。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem, IxToggle } from '@platform/ui-components';
import FlowGraph from '../../components/FlowGraph.vue';
import RuleEditor from '../../components/RuleEditor.vue';
import { useDraftStore } from '../../stores/draft';
import {
  fetchCategories,
  fetchDimensions,
  fetchFields,
  fetchProcessList,
} from '../../mock/service';
import { toastMsg } from '../../utils/feedback';
import type {
  Dimension,
  DimensionCategory,
  FieldDef,
  NodeFormField,
  NodeType,
  ProcessDef,
} from '../../types/schema';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

/** 草稿 store（与向导共享同一份内存草稿） */
const store = useDraftStore();
const { nodes, links } = storeToRefs(store);

/** 当前流程 id（菜单直达时为 0，选流程后赋值） */
const processId = ref(Number(route.query.processId) || 0);
const nodeIdParam = (route.query.nodeId as string) || '';
const from = (route.query.from as string) || '';

/** 菜单直达：可选流程列表 */
const flowOptions = ref<ProcessDef[]>([]);
const pickedFlowId = ref('');

const defName = computed(() => store.basic.defName);
/** 字段库三级浏览：维度 -> 分类 -> 字段（真实维表数据） */
const fieldDimensions = ref<Dimension[]>([]);
const fieldCategories = ref<DimensionCategory[]>([]);
const libDimension = ref('');
const libCategory = ref('all');
const groupedLibrary = ref<FieldDef[]>([]);
const selectedKey = ref('');
/** 当前节点字段（store.nodeFields[key] 的引用：增删改直接落内存草稿） */
const nodeFields = ref<NodeFormField[]>([]);
/** 同表单的其它字段（条件规则触发字段候选） */
function ruleFields(selfKey: string): { key: string; label: string }[] {
  return nodeFields.value.filter((x) => x.fieldKey !== selfKey).map((x) => ({ key: x.fieldKey, label: x.fieldName }));
}
const expandedKey = ref('');
const saving = ref(false);

const selectedNode = computed(() => nodes.value.find((n) => n.nodeKey === selectedKey.value));
/** 表单可配置节点：start/fill/approval；网关/结束无需表单 */
const FORM_NODE_TYPES = new Set<NodeType>(['start', 'fill', 'approval']);
const isNonFormNode = computed(() => {
  const node = selectedNode.value;
  return !!node && !FORM_NODE_TYPES.has(node.nodeType);
});
/** 已配置字段的节点（流程图角标，派生自 store） */
const configuredKeys = computed(() => store.configuredKeys);

/** 字段库级联：切维度 -> 重载分类与字段；切分类 -> 重载字段 */
watch(libDimension, async (v) => {
  if (!v) return;
  libCategory.value = 'all';
  fieldCategories.value = await fetchCategories(Number(v));
  await loadGroupedFields();
});

watch(libCategory, () => {
  void loadGroupedFields();
});

async function loadGroupedFields(): Promise<void> {
  if (!libDimension.value) {
    groupedLibrary.value = [];
    return;
  }
  const dimId = Number(libDimension.value);
  if (libCategory.value === 'all') {
    const cats = await fetchCategories(dimId);
    const fields: FieldDef[] = [];
    for (const c of cats) {
      fields.push(...(await fetchFields(c.id)));
    }
    groupedLibrary.value = fields;
  } else {
    groupedLibrary.value = await fetchFields(Number(libCategory.value));
  }
}

/** 菜单直达：选定流程后加载 */
watch(pickedFlowId, async (v) => {
  if (!v) return;
  processId.value = Number(v);
  await loadProcess();
});

/**
 * 加载流程草稿到 store：
 * - 向导跳转（from=wizard 且 store 已是该流程/新建草稿）→ 直接复用内存草稿（含未保存改动）；
 * - 菜单/列表入口 → 从后端加载。
 */
async function loadProcess(): Promise<void> {
  const pid = processId.value;
  if (pid > 0) {
    const reuseWizardDraft = from === 'wizard' && store.defId === pid;
    if (!reuseWizardDraft) {
      await store.loadFromServer(pid);
    }
  }
  if (!nodes.value.length) return;

  // 默认选中：query 指定的节点 > 第一个填写节点 > 第一个节点
  const exists = nodes.value.some((n) => n.nodeKey === nodeIdParam);
  const firstFill = nodes.value.find((n) => n.nodeType === 'fill');
  selectedKey.value = exists ? nodeIdParam : (firstFill?.nodeKey ?? nodes.value[0].nodeKey);
  syncNodeFields();
}

/** 同步当前选中节点的字段（引用 store 数组，增删改直接写内存草稿） */
function syncNodeFields(): void {
  nodeFields.value = store.getNodeFields(selectedKey.value);
  expandedKey.value = '';
}

onMounted(async () => {
  // 字段库：默认选中第一个维度
  fieldDimensions.value = await fetchDimensions();
  if (fieldDimensions.value.length) {
    libDimension.value = String(fieldDimensions.value[0].id);
    fieldCategories.value = await fetchCategories(Number(libDimension.value));
    await loadGroupedFields();
  }

  if (processId.value > 0 || from === 'wizard') {
    await loadProcess();
  } else {
    // 菜单直达：列出流程供选择
    flowOptions.value = await fetchProcessList();
  }
});

/** 点选流程图节点：切换显示对应节点字段（数据始终在 store，切走即存） */
function selectNode(key: string): void {
  if (key === selectedKey.value) return;
  selectedKey.value = key;
  syncNodeFields();
}

// ---- 字段增删改 ----
function isAdded(fieldKey: string): boolean {
  return nodeFields.value.some((f) => f.fieldKey === fieldKey);
}

function addField(def: FieldDef): void {
  if (isAdded(def.fieldKey)) return;
  nodeFields.value.push({
    id: Date.now() + nodeFields.value.length,
    fieldKey: def.fieldKey,
    fieldName: def.fieldName,
    fieldType: def.fieldType,
    mode: selectedNode.value?.nodeType === 'approval' ? 'readonly' : 'editable',
    required: def.required,
    rule: def.rule ? { ...def.rule } : undefined,
    placeholder: def.placeholder ?? '',
    sortNo: nodeFields.value.length + 1,
  });
  reorder();
}

function removeField(index: number): void {
  if (expandedKey.value === nodeFields.value[index]?.fieldKey) expandedKey.value = '';
  nodeFields.value.splice(index, 1);
  reorder();
}

function moveField(index: number, dir: -1 | 1): void {
  const target = index + dir;
  if (target < 0 || target >= nodeFields.value.length) return;
  const tmp = nodeFields.value[index];
  nodeFields.value[index] = nodeFields.value[target];
  nodeFields.value[target] = tmp;
  reorder();
}

function reorder(): void {
  nodeFields.value.forEach((f, i) => (f.sortNo = i + 1));
}

function toggleExpand(fieldKey: string): void {
  expandedKey.value = expandedKey.value === fieldKey ? '' : fieldKey;
}

/** 保存配置：落盘（整份草稿）；向导进入时保存后直接跳回向导第 5 步；菜单进入时留在本页 */
async function save(): Promise<void> {
  saving.value = true;
  try {
    await store.saveToServer();
    toastMsg(t('form.saved'));
    if (from === 'wizard') {
      const targetId = store.defId;
      const mode = targetId ? 'edit' : 'new';
      const idPart = targetId ? `&id=${targetId}` : '';
      router.push(`/workflow-admin/process/wizard?mode=${mode}${idPart}&step=5`);
    }
  } finally {
    saving.value = false;
  }
}

function goBack(): void {
  if (from === 'wizard') {
    router.back();
  } else {
    router.push('/workflow-admin/process');
  }
}
</script>

<style scoped>
.form-config {
  color: var(--brand-text);
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.page-title {
  margin: 4px 0 0;
}
.page-subtitle {
  color: var(--brand-text-secondary);
  margin-top: 4px;
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
  font-size: 13px;
}
.empty {
  color: var(--brand-text-disabled);
  text-align: center;
  padding: 24px 0;
}

/* ---- 流程选择器（菜单直达） ---- */
.flow-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
}
.flow-picker__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.flow-picker__select {
  width: 320px;
}

/* ---- 卡片 ---- */
.card {
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
  padding: 12px 16px;
}
.card--flow {
  flex-shrink: 0;
  max-height: 250px;
  overflow: auto;
}
.card--grow {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.card__title {
  font-weight: 600;
  margin-bottom: 8px;
}
.card__sub {
  font-size: 13px;
  color: var(--brand-text-secondary);
  font-weight: 400;
}
.approval-hint {
  padding: 24px;
  text-align: center;
  color: var(--brand-text-secondary);
  background: var(--brand-bg-tertiary);
  border-radius: var(--brand-radius);
}

/* ---- 下卡片两栏 ---- */
.field-area {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.field-lib {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  overflow: hidden;
}
.col-title {
  padding: 10px 12px;
  border-bottom: 1px solid var(--brand-border);
  font-weight: 600;
  font-size: 13px;
}
.col-title__hint {
  font-size: 12px;
  color: var(--brand-text-secondary);
  font-weight: 400;
  margin-left: 8px;
}
.field-lib__list {
  max-height: 400px;
  overflow: auto;
}
.field-lib__filters {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--brand-border);
}
.field-lib__filter {
  flex: 1;
  min-width: 0;
}
.lib-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--brand-border);
}
.lib-row--added {
  opacity: 0.45;
}
.lib-row__name {
  font-size: 13px;
}
.lib-row__spacer {
  flex: 1;
}
.lib-row__btn {
  min-width: 32px;
}
.tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
}
.tag--type {
  background: var(--brand-bg-tertiary);
  color: var(--brand-text-secondary);
}

.field-selected {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  overflow: hidden;
}
.sel-block {
  border-bottom: 1px solid var(--brand-border);
}
.sel-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  flex-wrap: wrap;
}
.sel-row__index {
  width: 20px;
  color: var(--brand-text-secondary);
  font-size: 13px;
}
.sel-row__name {
  font-weight: 600;
  font-size: 13px;
}
.sel-row__mode {
  width: 120px;
}
.sel-row__req-label {
  font-size: 12px;
  color: var(--brand-text-secondary);
}
.sel-row__spacer {
  flex: 1;
}

/* 展开设置区 */
.sel-detail {
  padding: 12px 16px;
  background: var(--brand-bg-secondary);
  border-top: 1px dashed var(--brand-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sel-detail__block {
  border-left: 3px solid var(--brand-border-strong);
  padding-left: 12px;
}
.sel-detail__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sel-detail__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.sel-detail__input {
  width: 260px;
}
</style>
