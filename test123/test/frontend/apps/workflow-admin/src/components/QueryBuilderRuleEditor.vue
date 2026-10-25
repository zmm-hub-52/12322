<template>
  <div class="qb-group" :class="{ 'qb-group--nested': removable }">
    <div v-if="node.kind === 'logic'" class="qb-panel">
      <div class="qb-head">
        <IxSelect v-model="opModel" class="qb-op">
          <IxSelectItem label="AND" value="and" />
          <IxSelectItem label="OR" value="or" />
        </IxSelect>
        <IxButton variant="secondary" @click="addCondition">+ 条件</IxButton>
        <IxButton variant="secondary" @click="addGroup">+ 条件组</IxButton>
        <span class="qb-spacer"></span>
        <IxButton v-if="removable" variant="secondary" class="qb-remove-group" @click="emit('remove')">×</IxButton>
      </div>

      <div class="qb-body">
        <template v-for="(child, i) in children" :key="i">
          <QueryBuilderRuleEditor
            v-if="child.kind === 'logic'"
            :model-value="child"
            :fields="fields"
            :removable="true"
            @update:model-value="updateChild(i, $event)"
            @remove="removeChild(i)"
          />

          <div v-else-if="child.kind === 'compare'" class="qb-row">
            <IxSelect v-model="rowFieldModels[i].value" class="qb-field">
              <IxSelectItem v-for="f in fields" :key="f.key" :label="f.label" :value="f.key" />
            </IxSelect>
            <IxSelect v-model="rowOpModels[i].value" class="qb-op">
              <IxSelectItem v-for="op in operatorsFor(rowFieldModels[i].value)" :key="op" :label="opLabel(op)" :value="op" />
            </IxSelect>

            <IxInput v-if="isBetween(child)" v-model="rowBetweenMinModels[i].value" class="qb-value qb-value--date" placeholder="最小值" />
            <IxInput v-if="isBetween(child)" v-model="rowBetweenMaxModels[i].value" class="qb-value qb-value--date" placeholder="最大值" />
            <IxInput v-else-if="!isNoValue(child.op)" v-model="rowValueModels[i].value" class="qb-value" :placeholder="valuePlaceholder(child)" />

            <IxButton variant="secondary" class="qb-row-remove" @click="removeChild(i)">×</IxButton>
          </div>

          <div v-else class="qb-readonly">只读节点：{{ describeNode(child) }}</div>
        </template>
      </div>
    </div>

    <div v-else class="qb-readonly">只读节点：{{ describeNode(node) }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import { and, condition, constant, field, or, type CompareOp, type ExprNode, type LogicOp } from '@platform/rule-engine';

defineOptions({ name: 'QueryBuilderRuleEditor' });

interface FieldOption { key: string; label: string; type?: string }

const props = defineProps<{
  modelValue: ExprNode;
  fields: FieldOption[];
  removable?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ExprNode): void;
  (e: 'remove'): void;
}>();

const node = computed(() => props.modelValue);
const children = computed<ExprNode[]>(() => (node.value.kind === 'logic' ? node.value.children : []));
const op = computed<LogicOp>(() => (node.value.kind === 'logic' ? node.value.op : 'and'));

const opModel = computed<LogicOp>({
  get: () => op.value,
  set: (v: LogicOp) => emit('update:modelValue', { ...(node.value as any), op: v }),
});

function defaultCondition(): ExprNode {
  return condition(props.fields[0]?.key ?? '', 'eq', '');
}

function addCondition(): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  emit('update:modelValue', { ...n, children: [...n.children, defaultCondition()] });
}

function addGroup(): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  emit('update:modelValue', { ...n, children: [...n.children, and()] });
}

function updateChild(index: number, child: ExprNode): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  const next = [...n.children];
  if (child.kind === 'logic' && child.children.length === 0) {
    next.splice(index, 1);
  } else {
    next[index] = child;
  }
  emit('update:modelValue', { ...n, children: next });
}

function removeChild(index: number): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  const next = n.children.filter((_, i) => i !== index);
  emit('update:modelValue', { ...n, children: next });
}

function operatorsFor(fieldKey: string): CompareOp[] {
  const f = props.fields.find((x) => x.key === fieldKey);
  if (f?.type === 'number') return ['gt', 'gte', 'lt', 'lte', 'eq', 'neq', 'between'];
  return ['eq', 'neq', 'contains', 'isEmpty', 'isNotEmpty', 'in', 'matches'];
}

function opLabel(op: CompareOp): string {
  const map: Record<string, string> = {
    eq: '=', neq: '≠', gt: '>', gte: '≥', lt: '<', lte: '≤',
    contains: '包含', isEmpty: '为空', isNotEmpty: '非空', in: 'in', matches: '正则', between: 'between',
  };
  return map[op] ?? op;
}

function isNoValue(op: string): boolean {
  return op === 'isEmpty' || op === 'isNotEmpty';
}

function isBetween(child: ExprNode): boolean {
  return child.kind === 'compare' && child.op === 'between';
}

function valuePlaceholder(child: ExprNode): string {
  return child.kind === 'compare' && child.op === 'in' ? '逗号分隔多个值' : '请输入值';
}

const rowFieldModels = computed(() => children.value.map((c, i) => computed({
  get: () => (c.kind === 'compare' && c.left.kind === 'field' ? c.left.fieldKey : ''),
  set: (v: string) => { const n = node.value; if (n.kind !== 'logic') return; const kids = [...n.children]; const cc = kids[i]; if (cc.kind === 'compare') kids[i] = { ...cc, left: field(v) }; emit('update:modelValue', { ...n, children: kids }); },
})));

const rowOpModels = computed(() => children.value.map((c, i) => computed({
  get: () => (c.kind === 'compare' ? c.op : 'eq'),
  set: (v: string) => setOp(i, v),
})));

const rowValueModels = computed(() => children.value.map((c, i) => computed({
  get: () => valueText(c),
  set: (v: string) => setValue(i, v),
})));

const rowBetweenMinModels = computed(() => children.value.map((c, i) => computed({
  get: () => betweenText(c, 0),
  set: (v: string) => setBetween(i, v, betweenText(c, 1)),
})));

const rowBetweenMaxModels = computed(() => children.value.map((c, i) => computed({
  get: () => betweenText(c, 1),
  set: (v: string) => setBetween(i, betweenText(c, 0), v),
})));

function setOp(index: number, op: string): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  const kids = [...n.children];
  const c = kids[index];
  if (c.kind !== 'compare') return;
  const next: any = { ...c, op: op as CompareOp };
  if (op === 'isEmpty' || op === 'isNotEmpty') next.right = undefined;
  else if (op === 'in' || op === 'between') next.right = constant([]);
  else if (!next.right || next.right.kind !== 'const') next.right = constant('');
  kids[index] = next;
  emit('update:modelValue', { ...n, children: kids });
}

function setValue(index: number, text: string): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  const kids = [...n.children];
  const c = kids[index];
  if (c.kind !== 'compare') return;
  if (c.op === 'in') {
    const arr = text.split(',').map((s) => s.trim()).filter(Boolean);
    kids[index] = { ...c, right: constant(arr) };
  } else {
    kids[index] = { ...c, right: constant(text) };
  }
  emit('update:modelValue', { ...n, children: kids });
}

function setBetween(index: number, min: string, max: string): void {
  const n = node.value;
  if (n.kind !== 'logic') return;
  const kids = [...n.children];
  const c = kids[index];
  if (c.kind !== 'compare') return;
  kids[index] = { ...c, right: constant([min, max]) };
  emit('update:modelValue', { ...n, children: kids });
}

function valueText(c: ExprNode): string {
  if (c.kind !== 'compare' || !c.right || c.right.kind !== 'const') return '';
  const v = c.right.value;
  if (Array.isArray(v)) return v.map(String).join(',');
  return v === null || v === undefined ? '' : String(v);
}

function betweenText(c: ExprNode, idx: number): string {
  if (c.kind !== 'compare' || !c.right || c.right.kind !== 'const') return '';
  const v = c.right.value;
  if (Array.isArray(v)) return v[idx] == null ? '' : String(v[idx]);
  return '';
}

function describeNode(n: ExprNode): string {
  switch (n.kind) {
    case 'field': return n.fieldKey;
    case 'const': return String(n.value);
    case 'compare': return n.op;
    case 'logic': return n.op;
    case 'not': return 'not';
    case 'func': return n.name;
    default: return '';
  }
}
</script>

<style scoped>
.qb-group { width: 100%; }
.qb-panel { border: 1px solid var(--brand-border); border-radius: var(--brand-radius-lg); background: var(--brand-bg-secondary); padding: 10px; }
.qb-group--nested .qb-panel { background: var(--brand-bg-tertiary); }
.qb-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.qb-op { width: 110px; }
.qb-spacer { flex: 1; }
.qb-remove-group { min-width: 32px; }
.qb-body { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.qb-group--nested .qb-body { padding-left: 12px; border-left: 1px dashed var(--brand-border); }
.qb-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.qb-field { width: 180px; }
.qb-value { width: 200px; }
.qb-value--date { width: 150px; }
.qb-row-remove { min-width: 32px; }
.qb-readonly { font-size: 12px; color: var(--brand-text-secondary); }
</style>
