<template>
  <div class="form-renderer">
    <div v-for="f in visibleFields" :key="f.key" class="fr-field">
      <label class="fr-label">
        {{ f.label }}
        <span v-if="f.required && !allReadonly" class="fr-required">*</span>
      </label>

      <!-- 多行文本 -->
      <textarea
        v-if="f.type === 'textarea'"
        v-model="model[f.key]"
        :disabled="isReadonly(f)"
        class="fr-textarea"
        :placeholder="f.placeholder ?? ''"
      ></textarea>

      <!-- 枚举下拉（选项来自规则） -->
      <IxSelect
        v-else-if="f.type === 'select'"
        v-model="model[f.key]"
        :disabled="isReadonly(f)"
        :placeholder="f.placeholder ?? ''"
      >
        <IxSelectItem v-for="opt in optionsOf(f)" :key="opt" :label="opt" :value="opt" />
      </IxSelect>

      <!-- 编码范围（≤50 条：可输入下拉，边输入边过滤） -->
      <IxSelect
        v-else-if="isSmallCodeRange(f)"
        v-model="model[f.key]"
        :editable="true"
        :disabled="isReadonly(f)"
        :i18n-placeholder-editable="placeholderOf(f)"
      >
        <IxSelectItem v-for="code in codeRangeOptions(f)" :key="code" :label="code" :value="code" />
      </IxSelect>

      <!-- 其余（文本/数字/日期/编码范围>50） -->
      <IxInput
        v-else
        v-model="model[f.key]"
        :disabled="isReadonly(f)"
        :placeholder="placeholderOf(f)"
      />

      <p v-if="errors[f.key]" class="fr-error">{{ errors[f.key] }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 动态表单渲染器（使用端核心）：按 schema 字段渲染 iX 表单。
 * - 基础组件映射：boolean→IxToggle、textarea→textarea、select→IxSelect（枚举）；
 * - 规则（rule，取值+校验二合一）：enum→下拉选项、codeRange≤50→可输入下拉、codeRange>50→输入+提示、
 *   range→数字范围、pattern→正则；
 * - mode：hidden 不渲染；readonly 禁用；editable 可编辑；
 * - required + 规则前端校验（validate() 暴露给页面提交前调用）。
 */
import { computed, reactive, watch } from 'vue';
import { IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import type { SchemaField } from '../types/schema';

const props = defineProps<{
  /** 字段定义（快照） */
  fields: SchemaField[];
  /** 表单数据（双向） */
  modelValue: Record<string, unknown>;
  /** 整表只读（审批场景） */
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void;
}>();

/** 内部表单模型（v-model 绑定 + 变更同步到外部；any 兼容各类型字段值） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const model = reactive<Record<string, any>>({ ...props.modelValue });

const errors = reactive<Record<string, string>>({});

/** 编码范围生成列表上限（超过则退化为输入+校验） */
const CODE_RANGE_LIMIT = 50;

watch(
  () => props.modelValue,
  (val) => {
    // 内容相同则跳过（自己的 emit 回显），避免冗余回环
    if (JSON.stringify(val ?? {}) === JSON.stringify(model)) return;
    Object.keys(model).forEach((k) => delete model[k]);
    Object.assign(model, val ?? {});
  },
  { deep: true },
);

watch(
  model,
  () => {
    // 内容未变则不 emit
    if (JSON.stringify({ ...model }) === JSON.stringify(props.modelValue ?? {})) return;
    emit('update:modelValue', { ...model });
  },
  { deep: true },
);

/** 可见字段（mode != hidden） */
const visibleFields = computed(() => props.fields.filter((f) => f.mode !== 'hidden'));

const allReadonly = computed(() => !!props.readonly);

function isReadonly(f: SchemaField): boolean {
  return allReadonly.value || f.mode === 'readonly';
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

/** 编码范围生成编码列表（≤50 条） */
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
  if (f.description) return `${f.label}|${f.description}`;
  if (f.rule?.type === 'codeRange') {
    const r = f.rule;
    const pad = (n: number) => String(n).padStart(r.width ?? 2, '0');
    return `${r.prefix ?? ''}${pad(r.start ?? 0)} ~ ${r.prefix ?? ''}${pad(r.end ?? 0)}`;
  }
  if (f.type === 'date') return 'YYYY-MM-DD';
  if (f.type === 'number') return '请输入数字';
  return '';
}

/** 判断某条件（fieldKey/operator/value）在当前表单数据下是否成立 */
function matchCondition(cond: Record<string, unknown> | undefined, data: Record<string, unknown>): boolean {
  const c = cond ?? {};
  const trigger = data[String(c.fieldKey ?? '')];
  const tv = trigger === undefined || trigger === null ? '' : String(trigger);
  const cv = String(c.value ?? '');
  switch (String(c.operator ?? 'eq')) {
    case 'isEmpty':
      return tv === '';
    case 'isNotEmpty':
      return tv !== '';
    case 'contains':
      return tv.includes(cv);
    case 'neq':
      return tv !== cv;
    case 'gt':
      return Number(tv) > Number(cv);
    case 'gte':
      return Number(tv) >= Number(cv);
    case 'lt':
      return Number(tv) < Number(cv);
    case 'lte':
      return Number(tv) <= Number(cv);
    case 'eq':
    default:
      return tv === cv;
  }
}

/** 对单个取值约束（enum/codeRange/range/pattern）做校验，返回错误信息（无错返回 null） */
function checkValueRule(r: Record<string, unknown> | undefined, v: unknown, label: string): string | null {
  if (!r) return null;
  const s = String(v);
  switch (String(r.type ?? '')) {
    case 'enum':
      if (!((r.options as string[] | undefined) ?? []).includes(s)) return `${label} 枚举值不合法`;
      return null;
    case 'range': {
      const num = Number(s);
      if (!Number.isNaN(num)) {
        const min = r.min as number | undefined;
        const max = r.max as number | undefined;
        if (min !== undefined && num < min) return `${label} 不能小于 ${min}`;
        if (max !== undefined && num > max) return `${label} 不能大于 ${max}`;
      }
      return null;
    }
    case 'codeRange': {
      const prefix = String(r.prefix ?? '');
      if (prefix && !s.startsWith(prefix)) return `${label} 编码需以 ${prefix} 开头`;
      const n = Number(s.slice(prefix.length));
      if (Number.isNaN(n)) return `${label} 编码格式不正确`;
      const start = r.start as number | undefined;
      const end = r.end as number | undefined;
      if (start !== undefined && n < start) return `${label} 编码序号不能小于 ${start}`;
      if (end !== undefined && n > end) return `${label} 编码序号不能大于 ${end}`;
      return null;
    }
    case 'pattern': {
      const p = r.pattern as string | undefined;
      if (p) {
        try {
          if (!new RegExp(p).test(s)) return `${label} 格式不正确`;
        } catch {
          /* 非法正则忽略 */
        }
      }
      return null;
    }
    default:
      return null;
  }
}

/** 校验（返回是否通过） */
function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k]);
  let ok = true;
  for (const f of visibleFields.value) {
    if (isReadonly(f) || f.mode === 'hidden') continue;
    const v = model[f.key];
    const empty = v === undefined || v === null || v === '';
    // 条件必填：触发字段满足条件时本字段必填
    const condRequired = f.rule?.type === 'requiredWhen' && matchCondition(f.rule, model);
    if ((f.required || condRequired) && empty) {
      errors[f.key] = `${f.label} 为必填项`;
      ok = false;
      continue;
    }
    // 条件取值校验：命中第一条满足条件的 case 时，值必须等于期望值（空值视为不满足）
    if (f.rule?.type === 'valueWhen') {
      const hit = (f.rule.cases ?? []).find((c) => matchCondition(c, model));
      if (hit && String(v ?? '') !== hit.expect) {
        errors[f.key] = `${f.label} 当前应为 ${hit.expect}`;
        ok = false;
      }
    }
    if (empty) continue;

    const r = f.rule;
    const mainErr = checkValueRule(r, v, f.label);
    if (mainErr) {
      errors[f.key] = mainErr;
      ok = false;
    }
    // 条件必填附带的内层取值约束（命中条件且已填写时）
    if (condRequired && r?.rule) {
      const innerErr = checkValueRule(r.rule, v, f.label);
      if (innerErr) {
        errors[f.key] = innerErr;
        ok = false;
      }
    }
  }
  return ok;
}

defineExpose({ validate, getModel: () => ({ ...model }) });
</script>

<style scoped>
.form-renderer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.fr-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.fr-label {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.fr-required {
  color: var(--brand-danger);
}
.fr-textarea {
  min-height: 80px;
  padding: 8px 10px;
  border: 1px solid var(--brand-border-strong);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  color: var(--brand-text);
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
}
.fr-textarea:disabled {
  background: var(--brand-bg-secondary);
  color: var(--brand-text-secondary);
}
.fr-error {
  margin: 0;
  font-size: 12px;
  color: var(--brand-danger);
}
</style>
