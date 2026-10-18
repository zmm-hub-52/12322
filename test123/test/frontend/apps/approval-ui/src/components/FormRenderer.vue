<template>
  <div class="form-renderer">
    <div v-for="f in visibleFields" :key="f.key" class="fr-field">
      <label class="fr-label">
        {{ f.label }}
        <span v-if="f.required && !allReadonly" class="fr-required">*</span>
      </label>

      <!-- 下拉（枚举选项来自取值规则） -->
      <IxSelect
        v-if="f.type === 'select'"
        v-model="model[f.key]"
        :disabled="isReadonly(f)"
        :placeholder="f.placeholder ?? ''"
      >
        <IxSelectItem v-for="opt in optionsOf(f)" :key="opt" :label="opt" :value="opt" />
      </IxSelect>

      <!-- 开关 -->
      <IxToggle v-else-if="f.type === 'boolean'" v-model="model[f.key]" :disabled="isReadonly(f)" />

      <!-- 多行文本 -->
      <textarea
        v-else-if="f.type === 'textarea'"
        v-model="model[f.key]"
        :disabled="isReadonly(f)"
        class="fr-textarea"
        :placeholder="f.placeholder ?? ''"
      ></textarea>

      <!-- 其余（文本/数字/日期） -->
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
 * - type 映射组件：input/number/date→IxInput、textarea→textarea、select→IxSelect（枚举选项）、boolean→IxToggle
 * - mode：hidden 不渲染；readonly 禁用；editable 可编辑
 * - required/pattern 基础校验（validate() 暴露给页面提交前调用）
 */
import { computed, reactive, watch } from 'vue';
import { IxInput, IxSelect, IxSelectItem, IxToggle } from '@platform/ui-components';
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

watch(
  () => props.modelValue,
  (val) => {
    Object.keys(model).forEach((k) => delete model[k]);
    Object.assign(model, val ?? {});
  },
  { deep: true },
);

watch(
  model,
  () => {
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
  return f.valueRule?.options ?? [];
}

function placeholderOf(f: SchemaField): string {
  if (f.placeholder) return f.placeholder;
  if (f.type === 'date') return 'YYYY-MM-DD';
  if (f.type === 'number') return '请输入数字';
  return '';
}

/** 校验（返回是否通过） */
function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k]);
  let ok = true;
  for (const f of visibleFields.value) {
    if (isReadonly(f) || f.mode === 'hidden') continue;
    const v = model[f.key];
    const empty = v === undefined || v === null || v === '';
    if (f.required && empty) {
      errors[f.key] = `${f.label} 为必填项`;
      ok = false;
      continue;
    }
    if (!empty && f.validation?.pattern) {
      try {
        if (!new RegExp(f.validation.pattern).test(String(v))) {
          errors[f.key] = `${f.label} 格式不正确`;
          ok = false;
        }
      } catch {
        /* 非法正则忽略 */
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
