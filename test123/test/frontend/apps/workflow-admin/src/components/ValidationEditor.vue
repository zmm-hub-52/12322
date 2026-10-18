<template>
  <div class="validation-editor">
    <!-- 必填 -->
    <div class="ve-row">
      <label class="ve-label">{{ t('form.required') }}</label>
      <IxToggle v-model="rule.required" />
    </div>

    <!-- 长度 -->
    <div class="ve-row">
      <label class="ve-label">{{ t('validation.minLength') }}</label>
      <IxInput v-model="rule.minLength" class="ve-small" />
      <label class="ve-label ve-label--gap">{{ t('validation.maxLength') }}</label>
      <IxInput v-model="rule.maxLength" class="ve-small" />
    </div>

    <!-- 正则：常用预设 + 自定义 -->
    <div class="ve-row">
      <label class="ve-label">{{ t('validation.patternPresets') }}</label>
      <IxSelect v-model="preset" class="ve-preset">
        <IxSelectItem :label="t('validation.none')" value="none" />
        <IxSelectItem :label="t('validation.patternPhone')" value="phone" />
        <IxSelectItem :label="t('validation.patternEmail')" value="email" />
        <IxSelectItem :label="t('validation.patternDate')" value="date" />
        <IxSelectItem :label="t('validation.patternNumber')" value="number" />
        <IxSelectItem :label="t('validation.custom')" value="custom" />
      </IxSelect>
      <IxInput
        v-if="preset === 'custom'"
        v-model="rule.pattern"
        class="ve-pattern"
        :placeholder="t('validation.pattern')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 校验规则可视化编辑器（必填/长度/正则，用户零 JSON）
 * 说明：与 ValueRuleEditor 配套，字段库维护与节点字段配置两处复用。
 */
import { reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { IxInput, IxSelect, IxSelectItem, IxToggle } from '@platform/ui-components';
import type { ValidationRule } from '../types/schema';

const props = defineProps<{
  /** 当前校验规则（JSON 对象） */
  modelValue?: ValidationRule;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ValidationRule | undefined): void;
}>();

const { t } = useI18n();

/** 常用正则预设 */
const PRESET_PATTERNS = {
  phone: '^1[3-9]\\d{9}$',
  email: '^[\\w.+-]+@[\\w-]+\\.[\\w.]+$',
  date: '^\\d{4}-\\d{2}-\\d{2}$',
  number: '^\\d+$',
} as const;

type PresetKey = keyof typeof PRESET_PATTERNS;

const rule = reactive<{ required?: boolean; minLength?: string; maxLength?: string; pattern?: string }>({
  required: false,
  minLength: '',
  maxLength: '',
  pattern: '',
});

const preset = ref<PresetKey | 'none' | 'custom'>('none');

/** 初始化回填期间屏蔽 watch 触发 */
let initializing = true;

// 外部规则 -> 内部状态
watch(
  () => props.modelValue,
  (val) => {
    rule.required = val?.required ?? false;
    rule.minLength = val?.minLength === undefined ? '' : String(val.minLength);
    rule.maxLength = val?.maxLength === undefined ? '' : String(val.maxLength);
    const p = val?.pattern;
    if (!p) {
      preset.value = 'none';
    } else {
      const hit = (Object.keys(PRESET_PATTERNS) as PresetKey[]).find((k) => PRESET_PATTERNS[k] === p);
      preset.value = hit ?? 'custom';
    }
    rule.pattern = p ?? '';
    initializing = false;
  },
  { immediate: true },
);

/** 组装输出（全空则输出 undefined） */
function build(): ValidationRule | undefined {
  const out: ValidationRule = {};
  if (rule.required) out.required = true;
  if (rule.minLength !== '' && Number(rule.minLength) >= 0) out.minLength = Number(rule.minLength);
  if (rule.maxLength !== '' && Number(rule.maxLength) >= 0) out.maxLength = Number(rule.maxLength);
  if (preset.value === 'custom' && rule.pattern) out.pattern = rule.pattern;
  if (preset.value !== 'custom' && preset.value !== 'none' && PRESET_PATTERNS[preset.value]) {
    out.pattern = PRESET_PATTERNS[preset.value];
  }
  return Object.keys(out).length ? out : undefined;
}

function emitValue(): void {
  emit('update:modelValue', build());
}

// 用户交互变化 -> 序列化 emit
watch(
  [() => rule.required, () => rule.minLength, () => rule.maxLength, () => rule.pattern],
  () => {
    if (initializing) return;
    emitValue();
  },
);

watch(preset, (v, old) => {
  if (initializing || v === old) return;
  if (v !== 'custom' && v !== 'none') {
    rule.pattern = PRESET_PATTERNS[v];
  }
  emitValue();
});
</script>

<style scoped>
.validation-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ve-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ve-label {
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.ve-label--gap {
  margin-left: 12px;
}
.ve-small {
  width: 100px;
}
.ve-preset {
  width: 200px;
}
.ve-pattern {
  width: 260px;
}
</style>
