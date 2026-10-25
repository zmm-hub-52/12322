<template>
  <div class="dte">
    <table class="dte-table">
      <thead>
        <tr>
          <th>条件字段</th>
          <th>比较符</th>
          <th>条件值</th>
          <th>期望值</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, i) in cases" :key="i">
          <td>
            <IxSelect v-model="row.fieldKey" class="dte-select">
              <IxSelectItem v-for="f in fields" :key="f.key" :label="f.label" :value="f.key" />
            </IxSelect>
          </td>
          <td>
            <IxSelect v-model="row.operator" class="dte-op">
              <IxSelectItem v-for="op in operators" :key="op.value" :label="op.label" :value="op.value" />
            </IxSelect>
          </td>
          <td>
            <IxInput v-if="!isNoValue(row.operator)" v-model="row.value" class="dte-input" />
            <span v-else class="dte-empty">—</span>
          </td>
          <td><IxInput v-model="row.expect" class="dte-input" /></td>
          <td><IxButton variant="secondary" @click="remove(i)">×</IxButton></td>
        </tr>
      </tbody>
    </table>
    <IxButton variant="secondary" @click="add">＋ 规则行</IxButton>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';

export interface DecisionCase { fieldKey: string; operator: string; value: string; expect: string }

const props = defineProps<{
  modelValue: DecisionCase[];
  fields: { key: string; label: string }[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: DecisionCase[]): void;
}>();

const cases = computed(() => props.modelValue);

const operators = [
  { value: 'eq', label: '=' },
  { value: 'neq', label: '≠' },
  { value: 'gt', label: '>' },
  { value: 'gte', label: '≥' },
  { value: 'lt', label: '<' },
  { value: 'lte', label: '≤' },
  { value: 'contains', label: '包含' },
  { value: 'isEmpty', label: '为空' },
  { value: 'isNotEmpty', label: '非空' },
];

function isNoValue(op: string): boolean {
  return op === 'isEmpty' || op === 'isNotEmpty';
}

function add(): void {
  const next = [...props.modelValue, { fieldKey: props.fields[0]?.key ?? '', operator: 'eq', value: '', expect: '' }];
  emit('update:modelValue', next);
}

function remove(index: number): void {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
}
</script>

<style scoped>
.dte { display: flex; flex-direction: column; gap: 8px; }
.dte-table { width: 100%; border-collapse: collapse; }
.dte-table th, .dte-table td { border: 1px solid var(--brand-border); padding: 6px 8px; text-align: left; font-size: 13px; }
.dte-table th { background: var(--brand-bg-secondary); color: var(--brand-text-secondary); }
.dte-select { width: 160px; }
.dte-op { width: 100px; }
.dte-input { width: 140px; }
.dte-empty { color: var(--brand-text-disabled); }
</style>
