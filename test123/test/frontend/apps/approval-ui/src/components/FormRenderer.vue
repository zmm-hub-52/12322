<template>
  <div class="form-renderer">
    <!-- form 模式：只显示第一行，页面为单笔表单 -->
    <div v-if="isForm" class="fr-form">
      <div v-for="f in columns" :key="f.key" class="fr-field">
        <label class="fr-label">
          {{ f.label }}
          <span v-if="f.required && !allReadonly" class="fr-required">*</span>
        </label>
        <IxSelect v-if="f.type === 'select'" v-model="firstRow[f.key]" :disabled="isReadonly(f)">
          <IxSelectItem v-for="opt in optionsOf(f)" :key="opt" :label="opt" :value="opt" />
        </IxSelect>
        <IxSelect
          v-else-if="isSmallCodeRange(f)"
          v-model="firstRow[f.key]"
          :editable="true"
          :disabled="isReadonly(f)"
        >
          <IxSelectItem v-for="code in codeRangeOptions(f)" :key="code" :label="code" :value="code" />
        </IxSelect>
        <IxInput
          v-else
          v-model="firstRow[f.key]"
          :disabled="isReadonly(f)"
          :placeholder="placeholderOf(f)"
        />
        <p v-if="errors[cellKey(0, f.key)]" class="fr-error">{{ errors[cellKey(0, f.key)] }}</p>
      </div>
    </div>

    <!-- table 模式：整表多行 -->
    <div v-else class="fr-table-wrap">
      <table class="fr-table">
        <thead>
          <tr>
            <th v-for="f in columns" :key="f.key">
              {{ f.label }}
              <span v-if="f.required && !allReadonly" class="fr-required">*</span>
            </th>
            <th v-if="!allReadonly" class="fr-op"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in rows" :key="ri">
            <td v-for="f in columns" :key="f.key">
              <IxSelect
                v-if="f.type === 'select'"
                v-model="row[f.key]"
                :disabled="isReadonly(f)"
              >
                <IxSelectItem v-for="opt in optionsOf(f)" :key="opt" :label="opt" :value="opt" />
              </IxSelect>

              <IxSelect
                v-else-if="isSmallCodeRange(f)"
                v-model="row[f.key]"
                :editable="true"
                :disabled="isReadonly(f)"
              >
                <IxSelectItem v-for="code in codeRangeOptions(f)" :key="code" :label="code" :value="code" />
              </IxSelect>

              <IxInput
                v-else
                v-model="row[f.key]"
                :disabled="isReadonly(f)"
                :placeholder="placeholderOf(f)"
              />

              <p v-if="errors[cellKey(ri, f.key)]" class="fr-error">{{ errors[cellKey(ri, f.key)] }}</p>
            </td>
            <td v-if="!allReadonly && isNewRow(row)" class="fr-op">
              <IxButton variant="secondary" @click="removeRow(ri)">×</IxButton>
            </td>
          </tr>
        </tbody>
      </table>
      <IxButton v-if="!allReadonly" variant="secondary" @click="addRow">＋ 行</IxButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import { evaluate } from '@platform/rule-engine';
import type { SchemaField } from '../types/schema';
import { compileFieldRule, compileRequiredRule } from '../utils/ruleTree';

const props = defineProps<{
  fields: SchemaField[];
  modelValue: { rows: Record<string, any>[] };
  readonly?: boolean;
  mode?: 'form' | 'table';
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: { rows: Record<string, any>[] }): void;
}>();

const model = reactive<{ rows: Record<string, any>[] }>({
  rows: Array.isArray(props.modelValue?.rows) ? props.modelValue.rows.map((r) => ({ ...r })) : [],
});

const errors = reactive<Record<string, string>>({});
const addedRows = ref<Set<Record<string, any>>>(new Set());
const CODE_RANGE_LIMIT = 50;

watch(
  () => props.modelValue,
  (val) => {
    if (JSON.stringify(val ?? { rows: [] }) === JSON.stringify(model)) return;
    model.rows = Array.isArray(val?.rows) ? val.rows.map((r) => ({ ...r })) : [];
    addedRows.value = new Set();
    model.rows.forEach((row) => fillDefaults(row));
  },
  { deep: true },
);

watch(
  model,
  () => {
    if (JSON.stringify({ rows: model.rows }) === JSON.stringify(props.modelValue ?? { rows: [] })) return;
    emit('update:modelValue', { rows: model.rows.map((r) => ({ ...r })) });
  },
  { deep: true },
);

const columns = computed(() => props.fields.filter((f) => f.mode !== 'hidden'));
const rows = computed(() => model.rows);
const allReadonly = computed(() => !!props.readonly);
const isForm = computed(() => props.mode === 'form');
const firstRow = computed(() => {
  if (!model.rows.length) {
    const row: Record<string, any> = {};
    fillDefaults(row);
    model.rows.push(row);
  }
  return model.rows[0];
});

function isReadonly(f: SchemaField): boolean {
  return allReadonly.value || f.mode === 'readonly';
}

function cellKey(ri: number, fieldKey: string): string {
  return ri + ':' + fieldKey;
}

function optionsOf(f: SchemaField): string[] {
  return f.rule?.type === 'enum' ? (f.rule.options ?? []) : [];
}

function isSmallCodeRange(f: SchemaField): boolean {
  const r = f.rule;
  if (r?.type !== 'codeRange') return false;
  const start = r.start ?? 1;
  const end = r.end ?? start;
  return end - start + 1 <= CODE_RANGE_LIMIT;
}

function codeRangeOptions(f: SchemaField): string[] {
  const r = f.rule;
  if (r?.type !== 'codeRange') return [];
  const start = r.start ?? 1;
  const end = r.end ?? start;
  const width = r.width ?? 2;
  const prefix = r.prefix ?? '';
  const codes: string[] = [];
  for (let i = start; i <= end; i++) {
    codes.push(prefix + String(i).padStart(width, '0'));
  }
  return codes;
}

function placeholderOf(f: SchemaField): string {
  if (f.placeholder) return f.placeholder;
  if (f.description) return f.label + '|' + f.description;
  if (f.type === 'date') return 'YYYY-MM-DD';
  if (f.type === 'number') return '请输入数字';
  return '';
}

function fillDefaults(row: Record<string, any>): void {
  for (const col of columns.value) {
    if (row[col.key] === undefined || row[col.key] === null || row[col.key] === '') {
      row[col.key] = col.defaultValue ?? '';
    }
  }
}

function isNewRow(row: Record<string, any>): boolean {
  return addedRows.value.has(row);
}

function addRow(): void {
  const row: Record<string, any> = {};
  fillDefaults(row);
  model.rows.push(row);
  addedRows.value.add(row);
}

function removeRow(index: number): void {
  const row = model.rows[index];
  if (row) addedRows.value.delete(row);
  model.rows.splice(index, 1);
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k]);
  let ok = true;
  for (const f of columns.value) {
    if (isReadonly(f)) continue;
    rows.value.forEach((row, ri) => {
      const v = row[f.key];
      const empty = v === undefined || v === null || v === '';
      const key = cellKey(ri, f.key);
      if (empty) {
        if (!evaluate(compileRequiredRule(f), row)) {
          errors[key] = f.label + ' 为必填项';
          ok = false;
        }
        return;
      }
      if (!evaluate(compileFieldRule(f), row)) {
        errors[key] = f.label + ' 校验不通过';
        ok = false;
      }
    });
  }
  return ok;
}

defineExpose({ validate, getModel: () => ({ rows: model.rows.map((r) => ({ ...r })) }) });
</script>

<style scoped>
.form-renderer { width: 100%; }
.fr-table-wrap { overflow-x: auto; }
.fr-table { width: 100%; border-collapse: collapse; }
.fr-table th, .fr-table td { border: 1px solid var(--brand-border); padding: 6px 8px; text-align: left; font-size: 13px; }
.fr-table th { background: var(--brand-bg-secondary); color: var(--brand-text-secondary); }
.fr-op { width: 40px; text-align: center; }
.fr-required { color: var(--brand-danger); }
.fr-form { display: flex; flex-direction: column; gap: 12px; }
.fr-field { display: flex; flex-direction: column; gap: 6px; }
.fr-label { font-size: 13px; color: var(--brand-text-secondary); }
.fr-error { margin: 2px 0 0; font-size: 12px; color: var(--brand-danger); }
</style>