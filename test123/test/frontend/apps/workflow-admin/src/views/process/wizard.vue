<template>
  <div class="wizard">
    <!-- 头部：返回 + 标题 + 版本提示 + 保存草稿 -->
    <div class="wizard-head">
      <a class="link" @click="goBack">← {{ t('wizard.back') }}</a>
      <h2 class="typography-h2 wizard-head__title">
        {{ isEdit ? t('wizard.editTitle') : t('wizard.createTitle') }}
      </h2>
      <span v-if="isEdit && store.published" class="wizard-head__hint">
        {{ t('wizard.publishedHint', { version: store.version }) }}
      </span>
      <div class="wizard-head__spacer"></div>
      <IxButton variant="secondary" @click="saveDraft">{{ t('wizard.saveDraft') }}</IxButton>
    </div>

    <!-- 步骤条（自绘，已完成步骤可点击回跳） -->
    <div class="steps">
      <template v-for="(s, i) in STEP_KEYS" :key="s">
        <div
          class="step"
          :class="{
            'step--active': step === i + 1,
            'step--done': step > i + 1,
            'step--clickable': step > i + 1,
          }"
          @click="onStepClick(i + 1)"
        >
          <span class="step__dot">{{ step > i + 1 ? '✓' : i + 1 }}</span>
          <span class="step__name">{{ t(`wizard.${s}`) }}</span>
        </div>
        <div v-if="i < STEP_KEYS.length - 1" class="step-line" :class="{ 'step-line--done': step > i + 1 }"></div>
      </template>
    </div>

    <!-- ==================== 第 1 步：基本信息 ==================== -->
    <div v-if="step === 1" class="panel">
      <div class="form-grid">
        <label class="field">
          <span class="field__label">{{ t('wizard.basicDefName') }} *</span>
          <IxInput v-model="basic.defName" :placeholder="t('wizard.basicDefNamePh')" />
        </label>
        <label class="field field--wide">
          <span class="field__label">{{ t('wizard.basicDesc') }}</span>
          <IxInput v-model="basic.description" :placeholder="t('wizard.basicDescPh')" />
        </label>
        <label class="field">
          <span class="field__label">{{ t('wizard.basicInitiator') }}</span>
          <IxSelect v-model="basic.initiator">
            <IxSelectItem :label="t('wizard.initiatorDept')" value="dept" />
            <IxSelectItem :label="t('wizard.initiatorBuyer')" value="buyer" />
            <IxSelectItem :label="t('wizard.initiatorQuality')" value="quality" />
          </IxSelect>
        </label>
      </div>
    </div>

    <!-- ==================== 第 2 步：流程模式模板 ==================== -->
    <div v-else-if="step === 2" class="panel">
      <p class="hint">{{ t('wizard.templateHint') }}</p>
      <div class="template-grid">
        <div
          v-for="tp in templates"
          :key="tp.key"
          class="template-card"
          :class="{ 'template-card--selected': selectedTemplateKey === tp.key }"
          @click="selectedTemplateKey = tp.key"
        >
          <div class="template-card__name">{{ tp.name }}</div>
          <div class="template-card__desc">{{ tp.description }}</div>
          <div class="template-card__nodes">{{ tp.nodes.map((n) => n.nodeName).join(' · ') }}</div>
        </div>
      </div>
    </div>

    <!-- ==================== 第 3 步：图形化节点编辑（DAG 画布） ==================== -->
    <div v-else-if="step === 3" class="panel">
      <p class="hint">
        {{ t('wizard.nodeHint') }}
        <span class="hint__sub">{{ t('wizard.nodeDragHint') }}</span>
      </p>
      <div v-if="showValidationError && nodeProblems.names.length" class="validate-banner">
        <span class="validate-banner__title">{{ t('wizard.validateFailed') }}</span>
        <span>{{ t('wizard.fixedAssigneeMissing', { names: nodeProblems.names.join('、') }) }}</span>
      </div>
      <div class="editor-wrap">
        <FlowGraph
          :nodes="nodes"
          :links="links"
          :selected-key="selectedNodeKey"
          :problem-keys="showValidationError ? nodeProblems.keys : []"
          :editable="true"
          @select="onSelectNode"
          @add-at="onAddAt"
        />
      </div>
    </div>

    <!-- ==================== 第 4 步：表单配置入口 ==================== -->
    <div v-else-if="step === 4" class="panel">
      <p class="hint">{{ t('wizard.formHint') }}</p>
      <div class="form-status-list">
        <div v-for="node in configurableNodes" :key="node.nodeKey" class="form-status-row">
          <span class="form-status-row__icon">{{ nodeIcon(node.nodeType) }}</span>
          <span class="form-status-row__name">{{ node.nodeName }}</span>
          <span class="tag" :class="`tag--${node.nodeType}`">{{ typeText(node.nodeType) }}</span>
          <span class="node-row__spacer"></span>
          <span v-if="(formCounts[node.nodeKey] ?? 0) > 0" class="form-status-row__ok">
            {{ t('wizard.formConfigured', { count: formCounts[node.nodeKey] }) }}
          </span>
          <span v-else class="form-status-row__warn">{{ t('wizard.formNotConfigured') }}</span>
          <IxButton variant="secondary" @click="goFormConfig(node)">{{ t('wizard.goFormConfig') }}</IxButton>
        </div>
      </div>
    </div>

    <!-- ==================== 第 5 步：确认发布 ==================== -->
    <div v-else-if="step === 5" class="panel">
      <p class="hint">{{ t('wizard.confirmHint') }}</p>
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-item__label">{{ t('wizard.summaryName') }}</span>
          <span class="summary-item__value">{{ basic.defName }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">{{ t('wizard.summaryInitiator') }}</span>
          <span class="summary-item__value">{{ initiatorText }}</span>
        </div>
        <div class="summary-item summary-item--wide">
          <span class="summary-item__label">{{ t('wizard.summaryVersion') }}</span>
          <span class="summary-item__value">{{ nextVersion }}</span>
        </div>
      </div>
      <FlowGraph
        :nodes="nodes"
        :links="links"
        :selected-key="null"
        :configured-keys="configuredKeys"
        class="wizard-chart"
      />
      <p v-if="unconfiguredFills.length" class="publish-warn">
        {{ t('wizard.publishBlocked', { names: unconfiguredFills.join('、') }) }}
      </p>
    </div>

    <!-- 底部按钮栏 -->
    <div class="wizard-foot">
      <IxButton variant="secondary" :disabled="step === 1" @click="onPrev">{{ t('wizard.prev') }}</IxButton>
      <div class="wizard-foot__spacer"></div>
      <IxButton v-if="step < 5" variant="primary" :disabled="!canNext" @click="onNext">
        {{ step === 4 ? t('wizard.finish') : t('wizard.next') }}
      </IxButton>
      <IxButton v-else variant="primary" :disabled="!canPublish || publishing" @click="publish">
        {{ t('wizard.publish') }}
      </IxButton>
    </div>

    <!-- ==================== 画布弹层（+ 菜单 / 节点操作） ==================== -->
    <div v-if="popover" class="popover-mask" @click="closePopover">
      <div class="popover" :style="{ left: `${popover.x}px`, top: `${popover.y}px` }" @click.stop>
        <!-- 连线中点「+」菜单 -->
        <template v-if="popover.kind === 'plus'">
          <div class="popover__title">{{ t('wizard.insertNode') }}</div>
          <button class="popover__item" @click="addNodeBetween(popover.fromKey!, popover.toKey!, 'fill')">
            <span class="popover__item-icon">✏️</span>{{ t('wizard.addFillNode') }}
          </button>
          <button class="popover__item" @click="addNodeBetween(popover.fromKey!, popover.toKey!, 'approval')">
            <span class="popover__item-icon">✔️</span>{{ t('wizard.addApprovalNode') }}
          </button>
          <button class="popover__item" @click="openParallelModal(popover.fromKey!, popover.toKey!)">
            <span class="popover__item-icon">⇉</span>{{ t('wizard.addParallel') }}
          </button>
          <button class="popover__item" @click="openExclusiveModal(popover.fromKey!, popover.toKey!)">
            <span class="popover__item-icon">×</span>{{ t('wizard.addExclusive') }}
          </button>
        </template>

        <!-- 节点操作菜单 -->
        <template v-else>
          <template v-if="popoverNode && popoverNode.nodeType === 'approval'">
            <div class="popover__title">{{ t('wizard.nodeSettings') }}</div>
            <IxInput v-model="popoverNode.nodeName" class="popover__rename" />
            <div class="popover__row">
              <label class="popover__label">{{ t('wizard.rejectStrategy') }}</label>
              <template v-if="isInParallelBranch(popoverNode.nodeKey)">
                <span class="popover__fixed">{{ t('wizard.rejectTerminate') }}</span>
              </template>
              <IxSelect v-else v-model="popoverNode.rejectStrategy" class="popover__select">
                <IxSelectItem
                  v-if="!isAfterParallelJoin(popoverNode.nodeKey)"
                  :label="t('wizard.rejectBackPrev')"
                  value="back_prev"
                />
                <IxSelectItem :label="t('wizard.rejectBackStarter')" value="back_starter" />
                <IxSelectItem :label="t('wizard.rejectTerminate')" value="terminate" />
              </IxSelect>
            </div>
            <p v-if="isInParallelBranch(popoverNode.nodeKey)" class="popover__hint">
              {{ t('wizard.parallelRejectFixed') }}
            </p>
            <p v-else-if="isAfterParallelJoin(popoverNode.nodeKey)" class="popover__hint">
              {{ t('wizard.afterJoinRejectHint') }}
            </p>
            <div class="popover__row">
              <label class="popover__label">{{ t('wizard.assigneeMode') }}</label>
              <template v-if="isAfterParallelJoin(popoverNode.nodeKey)">
                <span class="popover__fixed">{{ t('wizard.assigneeFixedMode') }}</span>
              </template>
              <IxSelect v-else v-model="popoverNode.assigneeMode" class="popover__select">
                <IxSelectItem :label="t('wizard.assigneeDynamic')" value="dynamic" />
                <IxSelectItem :label="t('wizard.assigneeFixedMode')" value="fixed" />
              </IxSelect>
            </div>
            <p v-if="isAfterParallelJoin(popoverNode.nodeKey)" class="popover__hint">
              {{ t('wizard.afterJoinAssigneeFixed') }}
            </p>
            <div v-if="popoverNode.assigneeMode === 'fixed'" class="popover__row">
              <label class="popover__label">{{ t('wizard.fixedAssignee') }}</label>
              <IxSelect v-model="popoverNode.fixedAssignee" class="popover__select">
                <IxSelectItem
                  v-for="u in userOptions"
                  :key="u.username"
                  :label="`${u.name}（${u.username}）`"
                  :value="u.username"
                />
              </IxSelect>
            </div>
            <p v-if="popoverNode.assigneeMode === 'fixed' && !popoverNode.fixedAssignee" class="popover__error">
              {{ t('wizard.fixedAssigneeRequired') }}
            </p>
            <div class="popover__actions">
              <IxButton variant="secondary" @click="moveNode(popoverNode.nodeKey, -1)">
                {{ t('wizard.moveUp') }}
              </IxButton>
              <IxButton variant="secondary" @click="moveNode(popoverNode.nodeKey, 1)">
                {{ t('wizard.moveDown') }}
              </IxButton>
              <IxButton variant="secondary" @click="removeNode(popoverNode.nodeKey)">
                {{ t('wizard.remove') }}
              </IxButton>
            </div>
          </template>
          <template v-else-if="popoverNode && popoverNode.nodeType === 'fill'">
            <div class="popover__title">{{ t('wizard.nodeSettings') }}</div>
            <IxInput v-model="popoverNode.nodeName" class="popover__rename" />
            <div class="popover__row">
              <label class="popover__label">{{ t('wizard.assigneeMode') }}</label>
              <template v-if="isAfterParallelJoin(popoverNode.nodeKey)">
                <span class="popover__fixed">{{ t('wizard.assigneeFixedMode') }}</span>
              </template>
              <IxSelect v-else v-model="popoverNode.assigneeMode" class="popover__select">
                <IxSelectItem :label="t('wizard.assigneeDynamic')" value="dynamic" />
                <IxSelectItem :label="t('wizard.assigneeFixedMode')" value="fixed" />
              </IxSelect>
            </div>
            <p v-if="isAfterParallelJoin(popoverNode.nodeKey)" class="popover__hint">
              {{ t('wizard.afterJoinAssigneeFixed') }}
            </p>
            <div v-if="popoverNode.assigneeMode === 'fixed'" class="popover__row">
              <label class="popover__label">{{ t('wizard.fixedAssignee') }}</label>
              <IxSelect v-model="popoverNode.fixedAssignee" class="popover__select">
                <IxSelectItem
                  v-for="u in userOptions"
                  :key="u.username"
                  :label="`${u.name}（${u.username}）`"
                  :value="u.username"
                />
              </IxSelect>
            </div>
            <p v-if="popoverNode.assigneeMode === 'fixed' && !popoverNode.fixedAssignee" class="popover__error">
              {{ t('wizard.fixedAssigneeRequired') }}
            </p>
            <div class="popover__actions">
              <IxButton variant="secondary" @click="moveNode(popoverNode.nodeKey, -1)">
                {{ t('wizard.moveUp') }}
              </IxButton>
              <IxButton variant="secondary" @click="moveNode(popoverNode.nodeKey, 1)">
                {{ t('wizard.moveDown') }}
              </IxButton>
              <IxButton variant="secondary" @click="removeNode(popoverNode.nodeKey)">
                {{ t('wizard.remove') }}
              </IxButton>
            </div>
          </template>
          <template v-else-if="popoverNode && isGateType(popoverNode.nodeType)">
            <div class="popover__title">{{ popoverNode.nodeName }}</div>
            <p class="popover__hint">{{ t('wizard.parallelGroupHint') }}</p>
            <IxButton
              v-if="isSplitType(popoverNode.nodeType)"
              variant="secondary"
              @click="openBranchEdit(popoverNode.nodeKey)"
            >
              {{ t('wizard.editBranch') }}
            </IxButton>
            <IxButton variant="secondary" @click="removeGatewayGroup(popoverNode.nodeKey)">
              {{ t('wizard.deleteParallel') }}
            </IxButton>
          </template>
          <template v-else>
            <div class="popover__title">{{ popoverNode?.nodeName ?? '' }}</div>
            <p class="popover__hint">{{ t('wizard.fixedNodeHint') }}</p>
          </template>
        </template>
      </div>
    </div>

    <!-- ==================== 排他分支配置弹层（条件编辑器） ==================== -->
    <div v-if="exclusiveModal" class="modal-mask" @click.self="closeExclusiveModal">
      <div class="modal modal--wide" @click.stop>
        <h3 class="modal__title">{{ t('wizard.exclusiveTitle') }}</h3>
        <p class="modal__hint">{{ t('wizard.exclusiveHint') }}</p>
        <div class="modal__row">
          <label class="modal__label">{{ t('wizard.parallelBranchCount') }}</label>
          <IxSelect v-model="exclusiveModal.branchCount" class="modal__select">
            <IxSelectItem label="2" value="2" />
            <IxSelectItem label="3" value="3" />
            <IxSelectItem label="4" value="4" />
          </IxSelect>
        </div>

        <!-- 每支线一个条件行：字段 / 比较符 / 值 / 设为默认 -->
        <div v-for="(cond, i) in exclusiveModal.conditions" :key="i" class="cond-row">
          <span class="cond-row__no">{{ t('wizard.condBranch', { n: i + 1 }) }}</span>
          <IxSelect v-model="cond.fieldKey" class="cond-row__field">
            <IxSelectItem v-for="f in fieldOptions" :key="f.fieldKey" :label="f.fieldName" :value="f.fieldKey" />
          </IxSelect>
          <IxSelect v-model="cond.operator" class="cond-row__op">
            <IxSelectItem
              v-for="op in operatorsFor(condFieldType(cond))"
              :key="op.value"
              :label="op.label"
              :value="op.value"
            />
          </IxSelect>
          <IxInput v-model="cond.value" class="cond-row__value" :placeholder="t('wizard.condValuePh')" />
          <label class="cond-row__default">
            <input
              type="radio"
              name="default-branch"
              :checked="exclusiveModal.defaultBranch === String(i)"
              @change="exclusiveModal.defaultBranch = String(i)"
            />
            {{ t('wizard.condAsDefault') }}
          </label>
        </div>
        <p v-if="!canConfirmExclusive" class="modal__error">{{ t('wizard.exclusiveInvalid') }}</p>
        <div class="modal__foot">
          <IxButton variant="secondary" @click="closeExclusiveModal">{{ t('common.cancel') }}</IxButton>
          <IxButton variant="primary" :disabled="!canConfirmExclusive" @click="confirmExclusive">
            {{ t('common.ok') }}
          </IxButton>
        </div>
      </div>
    </div>
    <div v-if="parallelModal" class="modal-mask" @click.self="closeParallelModal">
      <div class="modal" @click.stop>
        <h3 class="modal__title">{{ t('wizard.parallelTitle') }}</h3>
        <p class="modal__hint">{{ t('wizard.parallelHint') }}</p>
        <div class="modal__row">
          <label class="modal__label">{{ t('wizard.parallelBranchCount') }}</label>
          <IxSelect v-model="parallelModal.branchCount" class="modal__select">
            <IxSelectItem label="2" value="2" />
            <IxSelectItem label="3" value="3" />
            <IxSelectItem label="4" value="4" />
          </IxSelect>
        </div>
        <!-- 每支线节点类型：填写 / 审批 -->
        <div v-for="(bt, i) in parallelModal.branchTypes" :key="i" class="modal__row">
          <label class="modal__label">{{ t('wizard.branchType', { n: i + 1 }) }}</label>
          <IxSelect v-model="parallelModal.branchTypes[i]" class="modal__select">
            <IxSelectItem :label="t('wizard.nodeTypeFill')" value="fill" />
            <IxSelectItem :label="t('wizard.nodeTypeApproval')" value="approval" />
          </IxSelect>
        </div>
        <div class="modal__foot">
          <IxButton variant="secondary" @click="closeParallelModal">{{ t('common.cancel') }}</IxButton>
          <IxButton variant="primary" @click="confirmParallel">{{ t('common.ok') }}</IxButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 流程配置向导（Windows 安装向导式，5 步）
 * 1 基本信息 → 2 流程模式模板 → 3 图形化节点编辑（DAG 画布，支持并行）→ 4 表单配置入口 → 5 确认发布
 *
 * 设计要点：
 * - 第 3 步为自绘 SVG DAG 画布：连线中点「+」插入节点/并行分支，点击节点弹操作菜单；
 * - 并行 = 分叉(split) → N 条分支 → 汇合(join)，模板化生成，永远结构合法；
 * - 已发布流程编辑的是「下一版本草稿」，发布才触发版本 +1；
 * - fill 节点未配置表单则第 5 步禁止发布。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import FlowGraph from '../../components/FlowGraph.vue';
import { useDraftStore } from '../../stores/draft';
import { fetchAllFields, fetchTemplates, fetchUsers } from '../../mock/service';
import { toastMsg } from '../../utils/feedback';
import type { FieldDef, NodeType, ProcessLink, ProcessNode, ProcessTemplate } from '../../types/schema';

const STEP_KEYS = ['stepBasic', 'stepTemplate', 'stepNodes', 'stepForm', 'stepConfirm'] as const;

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

/** 草稿 store（内存唯一一份：图 + 基本信息 + 节点字段；保存才落盘） */
const store = useDraftStore();
const { basic, nodes, links } = storeToRefs(store);

const isEdit = (route.query.mode as string) === 'edit';
const defId = Number(route.query.id) || 0;

/** 初始步骤：支持 query.step（表单配置页保存后跳回第 5 步） */
const initStep = Number(route.query.step) || (isEdit ? 3 : 1);
const step = ref(Math.min(5, Math.max(1, initStep)));
const templates = ref<ProcessTemplate[]>([]);
const publishing = ref(false);

const selectedTemplateKey = ref('');
/** 当前已套用的模板 key（重新选不同模板时重新套用） */
const appliedTemplateKey = ref('');

// ---- 画布 UI 状态（本地；图数据在 store） ----
const selectedNodeKey = ref<string | null>(null);

/** 画布弹层状态 */
type PopoverState =
  | { kind: 'plus'; fromKey: string; toKey: string; x: number; y: number }
  | { kind: 'node'; key: string; x: number; y: number };

const popover = ref<PopoverState | null>(null);

/** 并行分支配置弹层（每支线可选填写/审批；editingKey 非空表示编辑现有分叉组） */
const parallelModal = ref<{
  fromKey: string;
  toKey: string;
  branchCount: string;
  branchTypes: ('fill' | 'approval')[];
  editingKey?: string;
} | null>(null);

/** 排他分支配置弹层（含每支条件编辑器；editingKey 非空表示编辑现有分叉组） */
const exclusiveModal = ref<{
  fromKey: string;
  toKey: string;
  branchCount: string;
  conditions: { fieldKey: string; operator: string; value: string }[];
  defaultBranch: string;
  editingKey?: string;
} | null>(null);

/** 条件编辑器字段选项（来自字段库） */
const fieldOptions = ref<FieldDef[]>([]);

/** 用户选项（固定处理人选择） */
const userOptions = ref<{ username: string; name: string }[]>([]);

/** 分支数变化时调整条件行数量 */
watch(
  () => exclusiveModal.value?.branchCount,
  (n) => {
    const m = exclusiveModal.value;
    if (!m || !n) return;
    const count = Number(n) || 2;
    while (m.conditions.length < count) m.conditions.push({ fieldKey: '', operator: 'gt', value: '' });
    if (m.conditions.length > count) m.conditions.length = count;
    if (Number(m.defaultBranch) >= count) m.defaultBranch = String(count - 1);
  },
);

/** 并行分支数变化时调整每支线类型数组长度 */
watch(
  () => parallelModal.value?.branchCount,
  (n) => {
    const m = parallelModal.value;
    if (!m || !n) return;
    const count = Number(n) || 2;
    while (m.branchTypes.length < count) m.branchTypes.push('approval');
    if (m.branchTypes.length > count) m.branchTypes.length = count;
  },
);

/** 节点表单配置完成度（派生自 store.nodeFields）：nodeKey -> 已配置字段数 */
const formCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {};
  for (const n of nodes.value) {
    if (n.nodeType === 'start' || n.nodeType === 'fill' || n.nodeType === 'approval') {
      counts[n.nodeKey] = (store.nodeFields[n.nodeKey] ?? []).length;
    }
  }
  return counts;
});

let nodeSeq = 0;
let linkSeq = 0;

onMounted(async () => {
  templates.value = await fetchTemplates();
  fieldOptions.value = await fetchAllFields();
  userOptions.value = await fetchUsers();
  if (isEdit && defId) {
    // 编辑模式：从后端加载草稿（图 + 各节点字段）
    await store.loadFromServer(defId);
  } else if (!store.loaded || !store.isNew) {
    // 新建模式：初始化空草稿（含自动生成编码）
    store.initNew();
  }
});

// ---- 派生状态 ----
const popoverNode = computed(() => {
  const p = popover.value;
  return p && p.kind === 'node' ? nodes.value.find((n) => n.nodeKey === p.key) : undefined;
});

/** 可配置表单的节点（start / fill / approval；网关/结束无需表单） */
const configurableNodes = computed(() =>
  nodes.value.filter((n) => n.nodeType === 'start' || n.nodeType === 'fill' || n.nodeType === 'approval'),
);

/** 已配置字段的节点 key 集合（流程图角标用） */
const configuredKeys = computed(() =>
  configurableNodes.value.filter((n) => (formCounts.value[n.nodeKey] ?? 0) > 0).map((n) => n.nodeKey),
);

/** 未配置字段的 fill 节点名（发布校验） */
const unconfiguredFills = computed(() =>
  nodes.value
    .filter((n) => n.nodeType === 'fill' && (formCounts.value[n.nodeKey] ?? 0) === 0)
    .map((n) => n.nodeName),
);

/** 节点级校验问题（固定处理人未指定人员等），供第三步「下一步」与发布前校验复用 */
function collectNodeProblems(): { keys: string[]; names: string[] } {
  const bad = nodes.value.filter(
    (n) => (n.nodeType === 'fill' || n.nodeType === 'approval') && n.assigneeMode === 'fixed' && !n.fixedAssignee,
  );
  return { keys: bad.map((n) => n.nodeKey), names: bad.map((n) => n.nodeName) };
}

const nodeProblems = computed(() => collectNodeProblems());

/** 第三步点过「下一步」后显示校验横幅/高亮（修复后自动消失） */
const showValidationError = ref(false);

const nextVersion = computed(() => store.version + 1);
const initiatorText = computed(() => {
  const map: Record<string, string> = { dept: 'initiatorDept', buyer: 'initiatorBuyer', quality: 'initiatorQuality' };
  return t(`wizard.${map[basic.value.initiator] ?? 'initiatorDept'}`);
});

// ---- 步骤校验 ----
const canNext = computed(() => {
  if (step.value === 1) return !!basic.value.defName.trim();
  if (step.value === 2) return isEdit || !!selectedTemplateKey.value;
  if (step.value === 3) return nodes.value.length >= 3;
  return true;
});

const canPublish = computed(() => unconfiguredFills.value.length === 0);

// ---- 步骤流转 ----
function onStepClick(target: number): void {
  if (target < step.value) step.value = target;
}

function onPrev(): void {
  if (step.value > 1) step.value -= 1;
}

async function onNext(): Promise<void> {
  if (!canNext.value) return;
  // 第 2 步 -> 第 3 步：按所选模板初始化节点与连线（重新选不同模板时重新套用）
  if (step.value === 2) {
    const tp = templates.value.find((x) => x.key === selectedTemplateKey.value);
    if (tp && appliedTemplateKey.value !== tp.key) {
      if (nodes.value.length > 0) {
        toastMsg(t('wizard.templateReset'));
      }
      nodeSeq = 0;
      linkSeq = 0;
      nodes.value = tp.nodes.map((n, i) => ({ ...n, id: i + 1 }));
      links.value = tp.links.map((l, i) => ({ ...l, id: i + 1 }));
      store.nodeFields = {};
      appliedTemplateKey.value = tp.key;
    }
  }
  // 第 3 步 -> 第 4 步：先校验节点配置，未通过则卡在第三步并醒目提示
  if (step.value === 3) {
    showValidationError.value = true;
    if (nodeProblems.value.names.length) {
      return;
    }
    showValidationError.value = false;
    const target = configurableNodes.value.find((n) => n.nodeType === 'fill') ?? configurableNodes.value[0];
    if (target) {
      goFormConfig(target);
      return;
    }
  }
  if (step.value < 5) {
    step.value += 1;
    if (step.value === 4) {
      /* 完成度派生自 store，无需加载 */
    }
  }
}

// ==================== 画布交互 ====================

/** 点击节点：选中 + 弹出操作菜单（审批节点补默认驳回策略） */
function onSelectNode(key: string, event: MouseEvent): void {
  selectedNodeKey.value = key;
  const node = nodes.value.find((n) => n.nodeKey === key);
  if (node?.nodeType === 'approval' && !node.rejectStrategy) {
    node.rejectStrategy = 'back_prev';
  }
  if (node && (node.nodeType === 'fill' || node.nodeType === 'approval') && !node.assigneeMode) {
    node.assigneeMode = 'dynamic';
  }
  // 并行相关约束（与后端 publish 校验一致）
  if (node) {
    if (node.nodeType === 'approval' && isInParallelBranch(node.nodeKey)) {
      node.rejectStrategy = 'terminate';
    }
    if (node.nodeType === 'approval' && isAfterParallelJoin(node.nodeKey) && node.rejectStrategy === 'back_prev') {
      node.rejectStrategy = 'terminate';
    }
    if ((node.nodeType === 'fill' || node.nodeType === 'approval') && isAfterParallelJoin(node.nodeKey)) {
      node.assigneeMode = 'fixed';
    }
  }
  popover.value = { kind: 'node', key, x: event.clientX, y: event.clientY };
}

/** 点击连线中点「+」：弹出插入菜单 */
function onAddAt(fromKey: string, toKey: string, event: MouseEvent): void {
  popover.value = { kind: 'plus', fromKey, toKey, x: event.clientX, y: event.clientY };
}

function closePopover(): void {
  popover.value = null;
}

function isEditableNode(type: NodeType): boolean {
  return type === 'fill' || type === 'approval';
}

function isGateType(type: NodeType): boolean {
  return (
    type === 'parallelSplit' ||
    type === 'parallelJoin' ||
    type === 'exclusiveSplit' ||
    type === 'exclusiveJoin'
  );
}

/** 分叉网关（可编辑分支的入口） */
function isSplitType(type: NodeType): boolean {
  return type === 'parallelSplit' || type === 'exclusiveSplit';
}

/** 解析分叉组：分叉 → 分支 → 汇合，返回前后节点与分支列表 */
function gatewayGroupInfo(splitKey: string): { fromKey: string; toKey: string; branches: ProcessNode[] } | null {
  const inLink = links.value.find((l) => l.toKey === splitKey);
  if (!inLink) return null;
  const branches = links.value
    .filter((l) => l.fromKey === splitKey)
    .map((l) => nodes.value.find((n) => n.nodeKey === l.toKey))
    .filter((n): n is ProcessNode => !!n);
  if (!branches.length) return null;
  const joinKey = findJoin(splitKey);
  if (!joinKey) return null;
  const outLink = links.value.find((l) => l.fromKey === joinKey);
  if (!outLink) return null;
  return { fromKey: inLink.fromKey, toKey: outLink.toKey, branches };
}

/** BFS：从分叉出发找汇合网关 */
function findJoin(splitKey: string): string {
  const queue = [splitKey];
  const visited = new Set<string>();
  while (queue.length) {
    const cur = queue.shift()!;
    if (visited.has(cur)) continue;
    visited.add(cur);
    for (const l of links.value) {
      if (l.fromKey !== cur) continue;
      const next = nodes.value.find((n) => n.nodeKey === l.toKey);
      if (!next) continue;
      if (next.nodeType === 'parallelJoin' || next.nodeType === 'exclusiveJoin') {
        return next.nodeKey;
      }
      queue.push(next.nodeKey);
    }
  }
  return '';
}

/** 编辑分叉组：预填当前配置并打开对应弹层 */
function openBranchEdit(splitKey: string): void {
  const node = nodes.value.find((n) => n.nodeKey === splitKey);
  if (!node) return;
  const info = gatewayGroupInfo(splitKey);
  if (!info) return;
  closePopover();

  if (node.nodeType === 'parallelSplit') {
    parallelModal.value = {
      fromKey: info.fromKey,
      toKey: info.toKey,
      branchCount: String(info.branches.length),
      branchTypes: info.branches.map((b) => (b.nodeType === 'approval' ? 'approval' : 'fill')),
      editingKey: splitKey,
    };
  } else if (node.nodeType === 'exclusiveSplit') {
    const conds = links.value
      .filter((l) => l.fromKey === splitKey)
      .map((l) =>
        l.condition
          ? { fieldKey: l.condition.fieldKey ?? '', operator: l.condition.operator ?? 'eq', value: l.condition.value ?? '' }
          : { fieldKey: '', operator: 'eq', value: '' },
      );
    const defaultIdx = links.value.filter((l) => l.fromKey === splitKey).findIndex((l) => l.condition?.isDefault);
    exclusiveModal.value = {
      fromKey: info.fromKey,
      toKey: info.toKey,
      branchCount: String(info.branches.length),
      conditions: conds,
      defaultBranch: String(defaultIdx >= 0 ? defaultIdx : Math.max(0, conds.length - 1)),
      editingKey: splitKey,
    };
  }
}

/** 节点是否位于并行分支内（反向回溯遇 parallelSplit）——并行分支驳回策略固定为终止 */
function isInParallelBranch(nodeKey: string): boolean {
  const byKey = new Map(nodes.value.map((n) => [n.nodeKey, n] as const));
  const inLinks = new Map<string, string[]>();
  for (const l of links.value) {
    const arr = inLinks.get(l.toKey) ?? [];
    arr.push(l.fromKey);
    inLinks.set(l.toKey, arr);
  }
  let cur: string | undefined = nodeKey;
  const visited = new Set<string>();
  while (cur && !visited.has(cur)) {
    visited.add(cur);
    const ins = inLinks.get(cur) ?? [];
    if (ins.length !== 1) return false;
    const prev = byKey.get(ins[0]);
    if (!prev) return false;
    if (prev.nodeType === 'parallelSplit') return true;
    if (prev.nodeType === 'start') return false;
    cur = prev.nodeKey; // fill/approval/网关继续回溯（跨过 userTask，覆盖分支内所有用户任务）
  }
  return false;
}

/** 节点是否为「并行汇合后的第一个用户任务」（反向只跨网关，先遇 parallelJoin 则 true） */
function isAfterParallelJoin(nodeKey: string): boolean {
  const byKey = new Map(nodes.value.map((n) => [n.nodeKey, n] as const));
  const inLinks = new Map<string, string[]>();
  for (const l of links.value) {
    const arr = inLinks.get(l.toKey) ?? [];
    arr.push(l.fromKey);
    inLinks.set(l.toKey, arr);
  }
  let cur: string | undefined = nodeKey;
  const visited = new Set<string>();
  while (cur && !visited.has(cur)) {
    visited.add(cur);
    const ins = inLinks.get(cur) ?? [];
    if (ins.length !== 1) return false;
    const prev = byKey.get(ins[0]);
    if (!prev) return false;
    if (prev.nodeType === 'parallelJoin') return true;
    if (
      prev.nodeType === 'parallelSplit' ||
      prev.nodeType === 'fill' ||
      prev.nodeType === 'approval' ||
      prev.nodeType === 'start'
    )
      return false;
    cur = prev.nodeKey; // 其它网关继续
  }
  return false;
}

/** 条件字段类型（决定比较符选项） */
function condFieldType(cond: { fieldKey: string }): string {
  return fieldOptions.value.find((f) => f.fieldKey === cond.fieldKey)?.fieldType ?? 'input';
}

/** 比较符选项：数字字段含大小比较，其余为相等/不等/包含 */
function operatorsFor(fieldType: string): { value: string; label: string }[] {
  if (fieldType === 'number') {
    return [
      { value: 'gt', label: '>' },
      { value: 'gte', label: '≥' },
      { value: 'lt', label: '<' },
      { value: 'lte', label: '≤' },
      { value: 'eq', label: '=' },
      { value: 'neq', label: '≠' },
    ];
  }
  return [
    { value: 'eq', label: '=' },
    { value: 'neq', label: '≠' },
    { value: 'contains', label: t('wizard.condContains') },
  ];
}

/** 确定可用：非默认支必须配置字段与比较值 */
const canConfirmExclusive = computed(() => {
  const m = exclusiveModal.value;
  if (!m) return false;
  return m.conditions.every((c, i) => {
    if (String(i) === m.defaultBranch) return true;
    return !!c.fieldKey && c.value !== '';
  });
});

/** 生成唯一 nodeKey（避让模板/已有节点的 key，防止渲染错位） */
function nextNodeKey(prefix: string): string {
  let n = 1;
  let key = `${prefix}_${n}`;
  while (nodes.value.some((x) => x.nodeKey === key)) {
    n += 1;
    key = `${prefix}_${n}`;
  }
  return key;
}

/** 在两节点之间插入填写/审批节点（断开原边，生成两条新边） */
function addNodeBetween(fromKey: string, toKey: string, type: 'fill' | 'approval'): void {
  nodeSeq += 1;
  const count = nodes.value.filter((n) => n.nodeType === type).length + 1;
  const node: ProcessNode = {
    id: -nodeSeq,
    nodeKey: nextNodeKey(type),
    nodeName: type === 'fill' ? `填写节点 ${count}` : `审批节点 ${count}`,
    nodeType: type,
  };
  const li = links.value.findIndex((l) => l.fromKey === fromKey && l.toKey === toKey);
  if (li < 0) {
    closePopover();
    return;
  }
  links.value.splice(li, 1);
  links.value.push({ id: ++linkSeq, fromKey, toKey: node.nodeKey });
  links.value.push({ id: ++linkSeq, fromKey: node.nodeKey, toKey });
  nodes.value.push(node);
  selectedNodeKey.value = node.nodeKey;
  closePopover();
}

/** 打开并行分支配置弹层 */
function openParallelModal(fromKey: string, toKey: string): void {
  parallelModal.value = { fromKey, toKey, branchCount: '2', branchTypes: ['approval', 'approval'] };
  closePopover();
}

/** 关闭并行分支配置弹层 */
function closeParallelModal(): void {
  parallelModal.value = null;
}

/** 生成并行结构：分叉 → N 条并行分支 → 汇合 */
function confirmParallel(): void {
  const modal = parallelModal.value;
  parallelModal.value = null;
  if (!modal) return;
  const { fromKey, toKey } = modal;
  const branchCount = Number(modal.branchCount) || 2;

  // 编辑模式：先删除旧分叉组（会桥接 fromKey→toKey）
  if (modal.editingKey) {
    removeGatewayGroup(modal.editingKey);
  }

  const li = links.value.findIndex((l) => l.fromKey === fromKey && l.toKey === toKey);
  if (li < 0) return;
  links.value.splice(li, 1);

  nodeSeq += 1;
  const split: ProcessNode = {
    id: -nodeSeq,
    nodeKey: nextNodeKey('split'),
    nodeName: '并行分叉',
    nodeType: 'parallelSplit',
  };
  nodeSeq += 1;
  const join: ProcessNode = {
    id: -nodeSeq,
    nodeKey: nextNodeKey('join'),
    nodeName: '并行汇合',
    nodeType: 'parallelJoin',
  };
  links.value.push({ id: ++linkSeq, fromKey, toKey: split.nodeKey });
  links.value.push({ id: ++linkSeq, fromKey: join.nodeKey, toKey });

  for (let b = 1; b <= branchCount; b++) {
    const branchType = modal.branchTypes[b - 1] ?? 'approval';
    nodeSeq += 1;
    const branchNode: ProcessNode = {
      id: -nodeSeq,
      nodeKey: nextNodeKey(branchType),
      nodeName: branchType === 'fill' ? `并行填写 ${b}` : `并行审批 ${b}`,
      nodeType: branchType,
      rejectStrategy: branchType === 'approval' ? 'terminate' : undefined,
    };
    links.value.push({ id: ++linkSeq, fromKey: split.nodeKey, toKey: branchNode.nodeKey });
    links.value.push({ id: ++linkSeq, fromKey: branchNode.nodeKey, toKey: join.nodeKey });
    nodes.value.push(branchNode);
  }
  nodes.value.push(split, join);
}

// ==================== 排他分支（条件分流） ====================

/** 打开排他分支配置弹层 */
function openExclusiveModal(fromKey: string, toKey: string): void {
  exclusiveModal.value = {
    fromKey,
    toKey,
    branchCount: '2',
    conditions: [
      { fieldKey: '', operator: 'gt', value: '' },
      { fieldKey: '', operator: 'eq', value: '' },
    ],
    defaultBranch: '1',
  };
  closePopover();
}

/** 关闭排他分支配置弹层 */
function closeExclusiveModal(): void {
  exclusiveModal.value = null;
}

/** 生成排他结构：分叉 → N 条条件分支（含默认支）→ 汇合 */
function confirmExclusive(): void {
  const modal = exclusiveModal.value;
  exclusiveModal.value = null;
  if (!modal) return;
  const { fromKey, toKey } = modal;
  const branchCount = Number(modal.branchCount) || 2;

  // 编辑模式：先删除旧分叉组（会桥接 fromKey→toKey）
  if (modal.editingKey) {
    removeGatewayGroup(modal.editingKey);
  }

  const li = links.value.findIndex((l) => l.fromKey === fromKey && l.toKey === toKey);
  if (li < 0) return;
  links.value.splice(li, 1);

  nodeSeq += 1;
  const split: ProcessNode = {
    id: -nodeSeq,
    nodeKey: nextNodeKey('xsplit'),
    nodeName: '排他分叉',
    nodeType: 'exclusiveSplit',
  };
  nodeSeq += 1;
  const join: ProcessNode = {
    id: -nodeSeq,
    nodeKey: nextNodeKey('xjoin'),
    nodeName: '排他汇合',
    nodeType: 'exclusiveJoin',
  };
  links.value.push({ id: ++linkSeq, fromKey, toKey: split.nodeKey });
  links.value.push({ id: ++linkSeq, fromKey: join.nodeKey, toKey });

  for (let b = 0; b < branchCount; b++) {
    const cond = modal.conditions[b] ?? { fieldKey: '', operator: 'eq', value: '' };
    const isDefault = String(b) === modal.defaultBranch;
    const fieldName = fieldOptions.value.find((f) => f.fieldKey === cond.fieldKey)?.fieldName ?? '';
    nodeSeq += 1;
    const branchNode: ProcessNode = {
      id: -nodeSeq,
      nodeKey: nextNodeKey('xbranch'),
      nodeName: isDefault ? `条件分支 ${b + 1}（默认）` : `条件分支 ${b + 1}`,
      nodeType: 'approval',
    };
    links.value.push({
      id: ++linkSeq,
      fromKey: split.nodeKey,
      toKey: branchNode.nodeKey,
      condition: isDefault
        ? { fieldKey: '', fieldName: '', operator: '', value: '', isDefault: true }
        : { fieldKey: cond.fieldKey, fieldName, operator: cond.operator, value: cond.value },
    });
    links.value.push({ id: ++linkSeq, fromKey: branchNode.nodeKey, toKey: join.nodeKey });
    nodes.value.push(branchNode);
  }
  nodes.value.push(split, join);
}

/** 沿唯一前驱回溯到链首（分界：非 fill/approval 或入边不唯一） */
function chainStart(key: string): string {
  let cur = key;
  for (;;) {
    const ins = links.value.filter((l) => l.toKey === cur);
    if (ins.length !== 1) break;
    const prev = nodes.value.find((n) => n.nodeKey === ins[0].fromKey);
    if (!prev || !isEditableNode(prev.nodeType)) break;
    cur = prev.nodeKey;
  }
  return cur;
}

/** 沿唯一后继前进到链尾 */
function chainEnd(key: string): string {
  let cur = key;
  for (;;) {
    const outs = links.value.filter((l) => l.fromKey === cur);
    if (outs.length !== 1) break;
    const next = nodes.value.find((n) => n.nodeKey === outs[0].toKey);
    if (!next || !isEditableNode(next.nodeType)) break;
    cur = next.nodeKey;
  }
  return cur;
}

/** 删除节点：并行组内唯一分支节点时整组删除；否则桥接前后 */
function removeNode(key: string): void {
  const node = nodes.value.find((n) => n.nodeKey === key);
  if (!node || node.nodeType === 'start' || node.nodeType === 'end') return;

  if (isGateType(node.nodeType)) {
    removeGatewayGroup(key);
    return;
  }

  // 是否在分支内且该支仅此一个节点（并行/排他通用）
  const ins = links.value.filter((l) => l.toKey === key);
  const outs = links.value.filter((l) => l.fromKey === key);
  const inSplit = ins.some((l) => isGateType(nodes.value.find((n) => n.nodeKey === l.fromKey)?.nodeType ?? 'fill'));
  const outJoin = outs.some((l) => isGateType(nodes.value.find((n) => n.nodeKey === l.toKey)?.nodeType ?? 'fill'));
  if (inSplit && outJoin) {
    removeGatewayGroup(key);
    return;
  }

  const fromKeys = ins.map((l) => l.fromKey);
  const toKeys = outs.map((l) => l.toKey);
  links.value = links.value.filter((l) => !ins.includes(l) && !outs.includes(l));
  for (const f of fromKeys) {
    for (const to of toKeys) {
      links.value.push({ id: ++linkSeq, fromKey: f, toKey: to });
    }
  }
  nodes.value = nodes.value.filter((n) => n.nodeKey !== key);
  if (selectedNodeKey.value === key) selectedNodeKey.value = null;
  closePopover();
}

/** 删除整个网关组（分叉 + 各分支 + 汇合，并行/排他通用），并桥接组前组后 */
function removeGatewayGroup(refKey: string): void {
  const refNode = nodes.value.find((n) => n.nodeKey === refKey);
  if (!refNode) return;

  let splitKey = '';
  let joinKey = '';
  if (refNode.nodeType === 'parallelSplit' || refNode.nodeType === 'exclusiveSplit') {
    splitKey = refKey;
    const firstOut = links.value.find((l) => l.fromKey === splitKey);
    const end = firstOut ? chainEnd(firstOut.toKey) : '';
    joinKey = end ? (links.value.find((l) => l.fromKey === end)?.toKey ?? '') : '';
  } else if (refNode.nodeType === 'parallelJoin' || refNode.nodeType === 'exclusiveJoin') {
    joinKey = refKey;
    const firstIn = links.value.find((l) => l.toKey === joinKey);
    const start = firstIn ? chainStart(firstIn.fromKey) : '';
    splitKey = start ? (links.value.find((l) => l.toKey === start)?.fromKey ?? '') : '';
  } else {
    const start = chainStart(refKey);
    const end = chainEnd(refKey);
    splitKey = links.value.find((l) => l.toKey === start)?.fromKey ?? '';
    joinKey = links.value.find((l) => l.fromKey === end)?.toKey ?? '';
  }
  if (!splitKey || !joinKey) return;

  // 收集组内全部节点（split BFS 到 join）
  const toRemove = new Set<string>();
  const queue = [splitKey];
  while (queue.length) {
    const cur = queue.shift()!;
    if (toRemove.has(cur)) continue;
    toRemove.add(cur);
    if (cur === joinKey) continue;
    for (const l of links.value.filter((x) => x.fromKey === cur)) queue.push(l.toKey);
  }

  const inLink = links.value.find((l) => l.toKey === splitKey);
  const outLink = links.value.find((l) => l.fromKey === joinKey);
  if (inLink && outLink) {
    links.value.push({ id: ++linkSeq, fromKey: inLink.fromKey, toKey: outLink.toKey });
  }
  links.value = links.value.filter((l) => !toRemove.has(l.fromKey) && !toRemove.has(l.toKey));
  nodes.value = nodes.value.filter((n) => !toRemove.has(n.nodeKey));
  if (toRemove.has(selectedNodeKey.value ?? '')) selectedNodeKey.value = null;
  closePopover();
}

/** 同链内上移/下移（与前驱/后继交换位置 = 重排三条边） */
function moveNode(key: string, dir: -1 | 1): void {
  const node = nodes.value.find((n) => n.nodeKey === key);
  if (!node || !isEditableNode(node.nodeType)) return;

  if (dir === -1) {
    const ins = links.value.filter((l) => l.toKey === key);
    if (ins.length !== 1) return;
    const prevKey = ins[0].fromKey;
    const prev = nodes.value.find((n) => n.nodeKey === prevKey);
    if (!prev || !isEditableNode(prev.nodeType)) return;
    const prevIns = links.value.filter((l) => l.toKey === prevKey);
    if (prevIns.length !== 1) return;
    const p2 = prevIns[0].fromKey;
    const out = links.value.find((l) => l.fromKey === key);
    if (!out) return;
    const nextKey = out.toKey;
    links.value = links.value.filter(
      (l) =>
        !(
          (l.fromKey === p2 && l.toKey === prevKey) ||
          (l.fromKey === prevKey && l.toKey === key) ||
          (l.fromKey === key && l.toKey === nextKey)
        ),
    );
    links.value.push({ id: ++linkSeq, fromKey: p2, toKey: key });
    links.value.push({ id: ++linkSeq, fromKey: key, toKey: prevKey });
    links.value.push({ id: ++linkSeq, fromKey: prevKey, toKey: nextKey });
  } else {
    const outs = links.value.filter((l) => l.fromKey === key);
    if (outs.length !== 1) return;
    const nextKey = outs[0].toKey;
    const next = nodes.value.find((n) => n.nodeKey === nextKey);
    if (!next || !isEditableNode(next.nodeType)) return;
    const nextOuts = links.value.filter((l) => l.fromKey === nextKey);
    if (nextOuts.length !== 1) return;
    const n2 = nextOuts[0].toKey;
    const inLink = links.value.find((l) => l.toKey === key);
    if (!inLink) return;
    const prevKey = inLink.fromKey;
    links.value = links.value.filter(
      (l) =>
        !(
          (l.fromKey === prevKey && l.toKey === key) ||
          (l.fromKey === key && l.toKey === nextKey) ||
          (l.fromKey === nextKey && l.toKey === n2)
        ),
    );
    links.value.push({ id: ++linkSeq, fromKey: prevKey, toKey: nextKey });
    links.value.push({ id: ++linkSeq, fromKey: nextKey, toKey: key });
    links.value.push({ id: ++linkSeq, fromKey: key, toKey: n2 });
  }
}

// ---- 跳转 / 保存 / 发布（全部基于 store 草稿） ----

/** 跳转到表单配置页（表单页读同一 store 草稿，无需传数据） */
function goFormConfig(node: ProcessNode): void {
  router.push(`/workflow-admin/form?processId=${store.defId || 0}&nodeId=${node.nodeKey}&from=wizard`);
}

/** 保存草稿：整个内存草稿一次落盘（图 + 基本信息 + 全部节点字段） */
async function saveDraft(): Promise<void> {
  await store.saveToServer();
  toastMsg(t('wizard.saved'));
}

/** 发布：先落盘再发布（后端版本+1+快照+BPMN 部署） */
async function publish(): Promise<void> {
  const problems = collectNodeProblems();
  if (problems.names.length) {
    toastMsg(t('wizard.fixedAssigneeMissing', { names: problems.names.join('、') }));
    return;
  }
  publishing.value = true;
  try {
    const version = await store.publish();
    toastMsg(t('wizard.published', { version }));
    goBack();
  } finally {
    publishing.value = false;
  }
}

function goBack(): void {
  router.push('/workflow-admin/process');
}

// ---- 展示辅助 ----
function nodeIcon(type: NodeType): string {
  const map: Record<NodeType, string> = {
    start: '▶',
    fill: '✏️',
    approval: '✔️',
    parallelSplit: '⇉',
    parallelJoin: '⇇',
    exclusiveSplit: '×',
    exclusiveJoin: '×',
    end: '⏹',
  };
  return map[type];
}

function typeText(type: NodeType): string {
  const map: Record<NodeType, string> = {
    start: 'wizard.nodeTypeStart',
    fill: 'wizard.nodeTypeFill',
    approval: 'wizard.nodeTypeApproval',
    parallelSplit: 'wizard.nodeTypeSplit',
    parallelJoin: 'wizard.nodeTypeJoin',
    exclusiveSplit: 'wizard.nodeTypeExSplit',
    exclusiveJoin: 'wizard.nodeTypeExJoin',
    end: 'wizard.nodeTypeEnd',
  };
  return t(map[type]);
}
</script>

<style scoped>
.wizard {
  color: var(--brand-text);
  display: flex;
  flex-direction: column;
  /* 父级 .app-content 提供确定高度，这里撑满；内部 .panel 滚动、底部按钮栏固定 */
  height: 100%;
}
.wizard-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.wizard-head__title {
  margin: 0;
}
.wizard-head__hint {
  font-size: 13px;
  color: var(--brand-warning);
}
.wizard-head__spacer,
.wizard-foot__spacer {
  flex: 1;
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}

/* ---- 步骤条 ---- */
.steps {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.step__dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid var(--brand-border-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--brand-text-secondary);
  background: var(--brand-bg);
}
.step__name {
  font-size: 14px;
  color: var(--brand-text-secondary);
}
.step--active .step__dot {
  border-color: var(--brand-primary);
  background: var(--brand-primary);
  color: var(--brand-primary-contrast);
}
.step--active .step__name {
  color: var(--brand-text);
  font-weight: 600;
}
.step--done .step__dot {
  border-color: var(--brand-primary);
  background: var(--brand-bg);
  color: var(--brand-primary);
}
.step--clickable {
  cursor: pointer;
}
.step-line {
  flex: 1;
  height: 2px;
  background: var(--brand-border);
  margin: 0 12px;
  min-width: 24px;
}
.step-line--done {
  background: var(--brand-primary);
}

/* ---- 面板 ---- */
.panel {
  flex: 1;
  overflow: auto;
  padding: 4px 2px 16px;
}
.hint {
  color: var(--brand-text-secondary);
  margin-bottom: 16px;
}
.hint__sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--brand-text-disabled);
}
.validate-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid var(--brand-danger);
  border-radius: var(--brand-radius);
  background: color-mix(in srgb, var(--brand-danger) 8%, var(--brand-bg));
  color: var(--brand-danger);
  font-size: 13px;
}
.validate-banner__title {
  font-weight: 600;
}

/* ---- 第 1 步 ---- */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  max-width: 720px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field--wide {
  grid-column: 1 / -1;
}
.field__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.field__error {
  font-size: 12px;
  color: var(--brand-warning);
}

/* ---- 第 2 步 ---- */
.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  max-width: 860px;
}
.template-card {
  border: 2px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  padding: 16px;
  cursor: pointer;
  background: var(--brand-bg-secondary);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.template-card:hover {
  border-color: var(--brand-border-strong);
}
.template-card--selected {
  border-color: var(--brand-primary);
  background: color-mix(in srgb, var(--brand-primary) 6%, var(--brand-bg));
}
.template-card__name {
  font-weight: 600;
  margin-bottom: 8px;
}
.template-card__desc {
  font-size: 13px;
  color: var(--brand-text-secondary);
  margin-bottom: 10px;
}
.template-card__nodes {
  font-size: 12px;
  color: var(--brand-primary);
}

/* ---- 第 3 步：画布 ---- */
.editor-wrap {
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
  padding: 8px;
  min-height: 220px;
}

/* ---- 第 4 步 ---- */
.form-status-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 860px;
}
.form-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg-secondary);
}
.form-status-row__icon {
  font-size: 16px;
}
.form-status-row__name {
  font-weight: 600;
  width: 220px;
}
.node-row__spacer {
  flex: 1;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag--start,
.tag--end,
.tag--parallelSplit,
.tag--parallelJoin {
  background: var(--brand-bg-tertiary);
  color: var(--brand-text-secondary);
}
.tag--fill {
  background: color-mix(in srgb, var(--brand-primary) 12%, var(--brand-bg));
  color: var(--brand-primary);
}
.tag--approval {
  background: color-mix(in srgb, var(--brand-warning) 15%, var(--brand-bg));
  color: var(--brand-warning);
}
.form-status-row__ok {
  font-size: 13px;
  color: var(--brand-success);
}
.form-status-row__warn {
  font-size: 13px;
  color: var(--brand-warning);
}

/* ---- 第 5 步 ---- */
.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 720px;
  margin-bottom: 20px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: var(--brand-bg-secondary);
  border-radius: var(--brand-radius);
}
.summary-item--wide {
  grid-column: 1 / -1;
}
.summary-item__label {
  font-size: 12px;
  color: var(--brand-text-secondary);
}
.summary-item__value {
  font-size: 14px;
  font-weight: 600;
}
.mono {
  font-family: monospace;
}
.wizard-chart {
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
  padding: 8px;
}
.publish-warn {
  margin-top: 12px;
  color: var(--brand-warning);
  font-size: 13px;
}

/* ---- 底部 ---- */
.wizard-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--brand-border);
}

/* ---- 弹层 ---- */
.popover-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
}
.popover {
  position: fixed;
  z-index: 101;
  min-width: 220px;
  background: var(--brand-bg);
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  box-shadow: var(--brand-shadow-lg);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.popover__title {
  font-size: 12px;
  color: var(--brand-text-secondary);
  padding-bottom: 4px;
  border-bottom: 1px solid var(--brand-border);
  font-weight: 600;
}
.popover__hint {
  font-size: 12px;
  color: var(--brand-text-secondary);
}
.popover__fixed {
  font-size: 13px;
  color: var(--brand-warning);
  font-weight: 600;
}
.popover__error {
  font-size: 12px;
  color: var(--brand-danger);
}
.popover__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  background: none;
  border-radius: var(--brand-radius);
  color: var(--brand-text);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
}
.popover__item:hover {
  background: var(--brand-bg-tertiary);
  color: var(--brand-primary);
}
.popover__item-icon {
  width: 18px;
}
.popover__rename {
  width: 200px;
}
.popover__actions {
  display: flex;
  gap: 6px;
}

/* ---- 并行配置弹层 ---- */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal {
  width: 420px;
  background: var(--brand-bg);
  border-radius: var(--brand-radius-lg);
  box-shadow: var(--brand-shadow-lg);
  padding: 20px;
}
.modal--wide {
  width: 620px;
}
.modal__title {
  margin: 0 0 8px;
}
.modal__hint {
  font-size: 13px;
  color: var(--brand-text-secondary);
  margin-bottom: 16px;
}
.modal__row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.modal__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.modal__select {
  width: 120px;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}
.modal__error {
  font-size: 12px;
  color: var(--brand-warning);
  margin: 0 0 8px;
}

/* ---- 条件编辑器行 ---- */
.cond-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.cond-row__no {
  width: 52px;
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.cond-row__field {
  width: 160px;
}
.cond-row__op {
  width: 90px;
}
.cond-row__value {
  width: 140px;
}
.cond-row__default {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--brand-text-secondary);
  cursor: pointer;
  white-space: nowrap;
}

/* ---- 节点弹层行 ---- */
.popover__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.popover__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.popover__select {
  flex: 1;
}
</style>
