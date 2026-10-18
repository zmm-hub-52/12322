<template>
  <div class="value-rule-editor">
    <!-- 规则类型 -->
    <div class="vr-row">
      <label class="vr-label">{{ t('valueRule.title') }}</label>
      <IxSelect v-model="ruleType" class="vr-type">
        <IxSelectItem :label="t('valueRule.none')" value="none" />
        <IxSelectItem :label="t('valueRule.enum')" value="enum" />
        <IxSelectItem :label="t('valueRule.codeRange')" value="codeRange" />
        <IxSelectItem :label="t('valueRule.range')" value="range" />
      </IxSelect>
    </div>

    <!-- 枚举：选项由运维手填 -->
    <template v-if="ruleType === 'enum'">
      <div class="vr-row">
        <label class="vr-label">{{ t('valueRule.options') }}</label>
      </div>
      <div class="vr-options">
        <div v-for="(opt, i) in enumOptions" :key="i" class="vr-option-row">
          <span class="vr-option-row__no">{{ i + 1 }}</span>
          <IxInput v-model="enumOptions[i]" class="vr-option-row__input" :placeholder="t('valueRule.optionPh')" />
          <IxButton variant="secondary" @click="removeOption(i)">×</IxButton>
        </div>
        <div class="vr-option-row">
          <IxButton variant="secondary" @click="addOption">{{ t('valueRule.addOption') }}</IxButton>
        </div>
      </div>
    </template>

    <!-- 编码范围：前缀 + 起止 + 位数 + 实时示例 -->
    <template v-else-if="ruleType === 'codeRange'">
      <div class="vr-row">
        <label class="vr-label">{{ t('valueRule.prefix') }}</label>
        <IxInput v-model="codeRule.prefix" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('valueRule.startNo') }}</label>
        <IxInput v-model="codeRule.start" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('valueRule.endNo') }}</label>
        <IxInput v-model="codeRule.end" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('valueRule.width') }}</label>
        <IxInput v-model="codeRule.width" class="vr-tiny" />
      </div>
      <div class="vr-row">
        <label class="vr-label">{{ t('valueRule.preview') }}</label>
        <span class="vr-preview__chip vr-preview__chip--mono">{{ codeExample }}</span>
      </div>
    </template>

    <!-- 数值范围 -->
    <template v-else-if="ruleType === 'range'">
      <div class="vr-row">
        <label class="vr-label">{{ t('valueRule.min') }}</label>
        <IxInput v-model="rangeRule.min" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('valueRule.max') }}</label>
        <IxInput v-model="rangeRule.max" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('valueRule.decimals') }}</label>
        <IxInput v-model="rangeRule.decimals" class="vr-tiny" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 取值规则可视化编辑器（用户零 JSON）
 * 说明：
 * 1. 通过「规则类型」下拉切换，动态渲染对应表单控件；
 * 2. 枚举选项由运维手填（A、B、C），编码范围/数值范围为结构化输入；
 * 3. 对外只暴露 ValueRule 对象（自动序列化，与后端 valueRule JSON 契约一致）；
 * 4. 字段配置与节点字段配置两处复用同一组件。
 */
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import type { ValueRule } from '../types/schema';

type RuleType = 'none' | 'enum' | 'codeRange' | 'range';

const props = defineProps<{
  /** 当前取值规则（JSON 对象） */
  modelValue?: ValueRule;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ValueRule | undefined): void;
}>();

const { t } = useI18n();

// ---- 内部状态 ----
const ruleType = ref<RuleType>('none');
const enumOptions = ref<string[]>([]);
const codeRule = reactive({ prefix: 'A', start: '1', end: '99', width: '2' });
const rangeRule = reactive({ min: '', max: '', decimals: '' });

/** 初始化回填期间屏蔽 watch 触发（避免 emit 相同值造成死循环） */
let initializing = true;

// 外部规则 -> 内部状态
watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      ruleType.value = 'none';
      return;
    }
    ruleType.value = val.type;
    if (val.type === 'enum') {
      const next = [...(val.options ?? [])];
      // 关键：内容相同则不赋新数组——打断「回填 → watch → emit → 回填」死循环
      if (JSON.stringify(next) !== JSON.stringify(enumOptions.value)) {
        enumOptions.value = next;
      }
    } else if (val.type === 'codeRange') {
      codeRule.prefix = val.prefix;
      codeRule.start = String(val.start);
      codeRule.end = String(val.end);
      codeRule.width = String(val.width);
    } else if (val.type === 'range') {
      rangeRule.min = val.min === undefined ? '' : String(val.min);
      rangeRule.max = val.max === undefined ? '' : String(val.max);
      rangeRule.decimals = val.decimals === undefined ? '' : String(val.decimals);
    }
    initializing = false;
  },
  { immediate: true },
);

/** 数字输入转 number（空串 -> undefined） */
function toNum(v: string): number | undefined {
  const n = Number(v);
  return v === '' || Number.isNaN(n) ? undefined : n;
}

function emitEnum(): void {
  emit('update:modelValue', { type: 'enum', options: [...enumOptions.value] });
}

function emitCodeRange(): void {
  emit('update:modelValue', {
    type: 'codeRange',
    prefix: codeRule.prefix,
    start: toNum(codeRule.start) ?? 0,
    end: toNum(codeRule.end) ?? 0,
    width: toNum(codeRule.width) ?? String(codeRule.end).length,
  });
}

function emitRange(): void {
  const decimals = toNum(rangeRule.decimals);
  emit('update:modelValue', {
    type: 'range',
    min: toNum(rangeRule.min) ?? 0,
    max: toNum(rangeRule.max) ?? 0,
    decimals,
  });
}

watch(ruleType, (v, old) => {
  if (initializing || v === old) return;
  if (v === 'none') {
    emit('update:modelValue', undefined);
  } else if (v === 'enum') {
    emitEnum();
  } else if (v === 'codeRange') {
    emitCodeRange();
  } else if (v === 'range') {
    emitRange();
  }
});

watch(enumOptions, () => {
  if (initializing || ruleType.value !== 'enum') return;
  // 保险：与当前 modelValue 内容相同则不 emit（双保险断循环）
  const cur = props.modelValue;
  if (cur && cur.type === 'enum' && JSON.stringify(cur.options) === JSON.stringify(enumOptions.value)) {
    return;
  }
  emitEnum();
});

watch([() => codeRule.prefix, () => codeRule.start, () => codeRule.end, () => codeRule.width], () => {
  if (initializing || ruleType.value !== 'codeRange') return;
  emitCodeRange();
});

watch([() => rangeRule.min, () => rangeRule.max, () => rangeRule.decimals], () => {
  if (initializing || ruleType.value !== 'range') return;
  emitRange();
});

// ---- 枚举选项增删 ----
function addOption(): void {
  enumOptions.value.push('');
}

function removeOption(index: number): void {
  enumOptions.value.splice(index, 1);
}

/** 编码示例：前缀 + 0 填充起止号 */
const codeExample = computed(() => {
  const start = toNum(codeRule.start) ?? 0;
  const end = toNum(codeRule.end) ?? 0;
  const width = toNum(codeRule.width) ?? 2;
  const pad = (n: number) => String(n).padStart(width, '0');
  return `${codeRule.prefix || ''}${pad(start)} ~ ${codeRule.prefix || ''}${pad(end)}`;
});
</script>

<style scoped>
.value-rule-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vr-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.vr-label {
  font-size: 13px;
  color: var(--brand-text-secondary);
  white-space: nowrap;
}
.vr-label--gap {
  margin-left: 12px;
}
.vr-type {
  width: 220px;
}
.vr-small {
  width: 120px;
}
.vr-tiny {
  width: 80px;
}
.vr-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  font-size: 13px;
  color: var(--brand-text);
}
.vr-preview__chip--mono {
  font-family: monospace;
  background: var(--brand-bg-secondary);
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  padding: 2px 8px;
}
.vr-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vr-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.vr-option-row__no {
  width: 20px;
  font-size: 12px;
  color: var(--brand-text-secondary);
}
.vr-option-row__input {
  width: 220px;
}
</style>
