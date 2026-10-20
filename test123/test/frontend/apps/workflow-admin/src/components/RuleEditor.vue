<template>
  <div class="rule-editor">
    <!-- 规则类型（取值 + 校验 二合一） -->
    <div class="vr-row">
      <label class="vr-label">{{ t('rule.typeLabel') }}</label>
      <IxSelect v-model="ruleType" class="vr-type">
        <IxSelectItem :label="t('rule.none')" value="none" />
        <IxSelectItem :label="t('rule.enum')" value="enum" />
        <IxSelectItem :label="t('rule.codeRange')" value="codeRange" />
        <IxSelectItem :label="t('rule.range')" value="range" />
        <IxSelectItem :label="t('rule.pattern')" value="pattern" />
        <IxSelectItem v-if="hasFields" :label="t('rule.requiredWhen')" value="requiredWhen" />
        <IxSelectItem v-if="hasFields" :label="t('rule.valueWhen')" value="valueWhen" />
      </IxSelect>
    </div>

    <!-- 下拉选项（选项由运维手填） -->
    <template v-if="ruleType === 'enum'">
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.options') }}</label>
      </div>
      <div class="vr-options">
        <div v-for="(opt, i) in enumOptions" :key="i" class="vr-option-row">
          <span class="vr-option-row__no">{{ i + 1 }}</span>
          <IxInput v-model="enumOptions[i]" class="vr-option-row__input" :placeholder="t('rule.optionPh')" />
          <IxButton variant="secondary" @click="removeOption(i)">×</IxButton>
        </div>
        <div class="vr-option-row">
          <IxButton variant="secondary" @click="addOption">{{ t('rule.addOption') }}</IxButton>
        </div>
      </div>
    </template>

    <!-- 编码范围：前缀 + 起止 + 位数 + 实时示例 -->
    <template v-else-if="ruleType === 'codeRange'">
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.prefix') }}</label>
        <IxInput v-model="codeRule.prefix" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.startNo') }}</label>
        <IxInput v-model="codeRule.start" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.endNo') }}</label>
        <IxInput v-model="codeRule.end" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.width') }}</label>
        <IxInput v-model="codeRule.width" class="vr-tiny" />
      </div>
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.preview') }}</label>
        <span class="vr-preview__chip--mono">{{ codeExample }}</span>
      </div>
    </template>

    <!-- 数值范围 -->
    <template v-else-if="ruleType === 'range'">
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.min') }}</label>
        <IxInput v-model="rangeRule.min" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.max') }}</label>
        <IxInput v-model="rangeRule.max" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.decimals') }}</label>
        <IxInput v-model="rangeRule.decimals" class="vr-tiny" />
      </div>
    </template>

    <!-- 正则：常用预设（默认手机号）+ 自定义 -->
    <template v-else-if="ruleType === 'pattern'">
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.patternPresets') }}</label>
        <IxSelect v-model="preset" class="vr-preset">
          <IxSelectItem :label="t('rule.presetPhone')" value="phone" />
          <IxSelectItem :label="t('rule.presetEmail')" value="email" />
          <IxSelectItem :label="t('rule.presetDate')" value="date" />
          <IxSelectItem :label="t('rule.presetNumber')" value="number" />
          <IxSelectItem :label="t('rule.presetCustom')" value="custom" />
        </IxSelect>
        <IxInput
          v-if="preset === 'custom'"
          v-model="customPattern"
          class="vr-pattern"
          :placeholder="t('rule.patternPh')"
        />
      </div>
    </template>

    <!-- 条件必填：触发字段满足条件时本字段必填 -->
    <template v-else-if="ruleType === 'requiredWhen'">
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.whenField') }}</label>
        <IxSelect v-model="reqWhen.fieldKey" class="vr-field">
          <IxSelectItem v-for="f in fieldOptions" :key="f.key" :label="f.label" :value="f.key" />
        </IxSelect>
        <label class="vr-label vr-label--gap">{{ t('rule.operator') }}</label>
        <IxSelect v-model="reqWhen.operator" class="vr-op">
          <IxSelectItem v-for="op in operators" :key="op" :label="opLabel(op)" :value="op" />
        </IxSelect>
        <label v-if="!isNoValue(reqWhen.operator)" class="vr-label vr-label--gap">{{ t('rule.whenValue') }}</label>
        <IxInput v-if="!isNoValue(reqWhen.operator)" v-model="reqWhen.value" class="vr-small" />
      </div>
      <div class="vr-row">
        <label class="vr-label">{{ t('rule.innerConstraint') }}</label>
        <IxSelect v-model="reqWhen.innerType" class="vr-type">
          <IxSelectItem :label="t('rule.none')" value="none" />
          <IxSelectItem :label="t('rule.enum')" value="enum" />
          <IxSelectItem :label="t('rule.codeRange')" value="codeRange" />
          <IxSelectItem :label="t('rule.range')" value="range" />
          <IxSelectItem :label="t('rule.pattern')" value="pattern" />
        </IxSelect>
      </div>
      <template v-if="reqWhen.innerType === 'enum'">
        <div v-for="(opt, i) in innerEnum" :key="i" class="vr-option-row">
          <span class="vr-option-row__no">{{ i + 1 }}</span>
          <IxInput v-model="innerEnum[i]" class="vr-option-row__input" :placeholder="t('rule.optionPh')" />
          <IxButton variant="secondary" @click="innerEnum.splice(i, 1)">×</IxButton>
        </div>
        <div class="vr-option-row">
          <IxButton variant="secondary" @click="innerEnum.push('')">{{ t('rule.addOption') }}</IxButton>
        </div>
      </template>
      <div v-else-if="reqWhen.innerType === 'codeRange'" class="vr-row">
        <label class="vr-label">{{ t('rule.prefix') }}</label>
        <IxInput v-model="innerCode.prefix" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.startNo') }}</label>
        <IxInput v-model="innerCode.start" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.endNo') }}</label>
        <IxInput v-model="innerCode.end" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.width') }}</label>
        <IxInput v-model="innerCode.width" class="vr-tiny" />
      </div>
      <div v-else-if="reqWhen.innerType === 'range'" class="vr-row">
        <label class="vr-label">{{ t('rule.min') }}</label>
        <IxInput v-model="innerRange.min" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.max') }}</label>
        <IxInput v-model="innerRange.max" class="vr-small" />
      </div>
      <div v-else-if="reqWhen.innerType === 'pattern'" class="vr-row">
        <IxInput v-model="innerPattern" class="vr-pattern" :placeholder="t('rule.patternPh')" />
      </div>
    </template>

    <!-- 条件取值校验：命中第一条满足条件的 case 时本字段必须等于 expect -->
    <template v-else-if="ruleType === 'valueWhen'">
      <div v-for="(c, i) in valueWhenCases" :key="i" class="vr-row">
        <label class="vr-label">{{ t('rule.whenField') }}</label>
        <IxSelect v-model="c.fieldKey" class="vr-field">
          <IxSelectItem v-for="f in fieldOptions" :key="f.key" :label="f.label" :value="f.key" />
        </IxSelect>
        <label class="vr-label vr-label--gap">{{ t('rule.operator') }}</label>
        <IxSelect v-model="c.operator" class="vr-op">
          <IxSelectItem v-for="op in operators" :key="op" :label="opLabel(op)" :value="op" />
        </IxSelect>
        <label v-if="!isNoValue(c.operator)" class="vr-label vr-label--gap">{{ t('rule.whenValue') }}</label>
        <IxInput v-if="!isNoValue(c.operator)" v-model="c.value" class="vr-small" />
        <label class="vr-label vr-label--gap">{{ t('rule.expect') }}</label>
        <IxInput v-model="c.expect" class="vr-small" />
        <IxButton variant="secondary" @click="removeCase(i)">×</IxButton>
      </div>
      <div class="vr-row">
        <IxButton variant="secondary" @click="addCase">{{ t('rule.addCase') }}</IxButton>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 字段规则可视化编辑器（取值 + 校验 二合一，用户零 JSON）。
 * - enum：下拉选项（选项由运维手填）；
 * - codeRange：编码范围（前缀+起止+位数）；
 * - range：数值范围；
 * - pattern：正则（手机号/邮箱等，默认手机号）。
 * 字段库维护与节点字段配置两处复用同一组件。
 *
 * 实现：规则 = 内部编辑状态的纯派生（computed），派生值变化时 emit；
 *       外部回填时用「内容比较」跳过自己的 echo，避免 watch 回环。
 */
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import type { Rule, ValueRule } from '../types/schema';

type RuleType = 'none' | 'enum' | 'codeRange' | 'range' | 'pattern' | 'requiredWhen' | 'valueWhen';

/** 常用正则预设 */
const PRESET_PATTERNS = {
  phone: '^1[3-9]\\d{9}$',
  email: '^[\\w.+-]+@[\\w-]+\\.[\\w.]+$',
  date: '^\\d{4}-\\d{2}-\\d{2}$',
  number: '^\\d+$',
} as const;

type PresetKey = keyof typeof PRESET_PATTERNS;

const props = defineProps<{
  modelValue?: Rule;
  /** 同表单的其它字段（条件规则触发字段候选；字段库级不传） */
  fields?: { key: string; label: string }[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: Rule | undefined): void;
}>();

const { t } = useI18n();

const fieldOptions = computed(() => props.fields ?? []);
const hasFields = computed(() => fieldOptions.value.length > 0);

const operators = ['eq', 'neq', 'contains', 'isEmpty', 'isNotEmpty', 'gt', 'gte', 'lt', 'lte'] as const;

function opLabel(op: string): string {
  const map: Record<string, string> = {
    eq: '=',
    neq: '≠',
    gt: '>',
    gte: '≥',
    lt: '<',
    lte: '≤',
    contains: t('rule.opContains'),
    isEmpty: t('rule.opEmpty'),
    isNotEmpty: t('rule.opNotEmpty'),
  };
  return map[op] ?? op;
}

function isNoValue(op: string): boolean {
  return op === 'isEmpty' || op === 'isNotEmpty';
}

// ---- 内部编辑状态（唯一真相源） ----
const ruleType = ref<RuleType>('none');
const enumOptions = ref<string[]>([]);
const codeRule = reactive({ prefix: 'A', start: '1', end: '99', width: '2' });
const rangeRule = reactive({ min: '', max: '', decimals: '' });
const preset = ref<PresetKey | 'custom'>('phone');
const customPattern = ref('');
const reqWhen = reactive<{
  fieldKey: string;
  operator: string;
  value: string;
  innerType: 'none' | 'enum' | 'codeRange' | 'range' | 'pattern';
}>({ fieldKey: '', operator: 'eq', value: '', innerType: 'none' });
const innerEnum = ref<string[]>([]);
const innerCode = reactive({ prefix: 'A', start: '1', end: '99', width: '2' });
const innerRange = reactive({ min: '', max: '' });
const innerPattern = ref('');
const valueWhenCases = ref<{ fieldKey: string; operator: string; value: string; expect: string }[]>([]);

// ---- 内层取值约束（requiredWhen 附带，单条） ----
const innerRule = computed<ValueRule | undefined>(() => {
  switch (reqWhen.innerType) {
    case 'enum':
      return { type: 'enum', options: [...innerEnum.value] };
    case 'codeRange':
      return {
        type: 'codeRange',
        prefix: innerCode.prefix,
        start: toNum(innerCode.start) ?? 0,
        end: toNum(innerCode.end) ?? 0,
        width: toNum(innerCode.width) ?? 2,
      };
    case 'range':
      return { type: 'range', min: toNum(innerRange.min) ?? 0, max: toNum(innerRange.max) ?? 0 };
    case 'pattern':
      return { type: 'pattern', pattern: innerPattern.value };
    default:
      return undefined;
  }
});

// ---- 规则 = 内部状态的纯派生 ----
const rule = computed<Rule | undefined>(() => {
  switch (ruleType.value) {
    case 'enum':
      return { type: 'enum', options: [...enumOptions.value] };
    case 'codeRange':
      return {
        type: 'codeRange',
        prefix: codeRule.prefix,
        start: toNum(codeRule.start) ?? 0,
        end: toNum(codeRule.end) ?? 0,
        width: toNum(codeRule.width) ?? 2,
      };
    case 'range':
      return {
        type: 'range',
        min: toNum(rangeRule.min) ?? 0,
        max: toNum(rangeRule.max) ?? 0,
        decimals: toNum(rangeRule.decimals),
      };
    case 'pattern':
      return {
        type: 'pattern',
        pattern: preset.value === 'custom' ? customPattern.value : PRESET_PATTERNS[preset.value as PresetKey],
      };
    case 'requiredWhen': {
      const inner = innerRule.value;
      return inner
        ? { type: 'requiredWhen', fieldKey: reqWhen.fieldKey, operator: reqWhen.operator, value: reqWhen.value, rule: inner }
        : { type: 'requiredWhen', fieldKey: reqWhen.fieldKey, operator: reqWhen.operator, value: reqWhen.value };
    }
    case 'valueWhen':
      return { type: 'valueWhen', cases: valueWhenCases.value.map((c) => ({ ...c })) };
    default:
      return undefined;
  }
});

// ---- 派生值变化 → emit（唯一一个 emit watch） ----
watch(rule, (v) => emit('update:modelValue', v), { deep: true });

// ---- 外部回填 → 内部状态（内容比较防 echo） ----
watch(
  () => props.modelValue,
  (val) => {
    if (JSON.stringify(val) === JSON.stringify(rule.value)) return;
    applyModel(val);
  },
  { immediate: true },
);

function applyModel(val: Rule | undefined): void {
  if (!val) {
    ruleType.value = 'none';
    return;
  }
  ruleType.value = val.type;
  if (val.type === 'enum') {
    enumOptions.value = [...(val.options ?? [])];
  } else if (val.type === 'codeRange') {
    codeRule.prefix = val.prefix;
    codeRule.start = String(val.start);
    codeRule.end = String(val.end);
    codeRule.width = String(val.width);
  } else if (val.type === 'range') {
    rangeRule.min = val.min === undefined ? '' : String(val.min);
    rangeRule.max = val.max === undefined ? '' : String(val.max);
    rangeRule.decimals = val.decimals === undefined ? '' : String(val.decimals);
  } else if (val.type === 'pattern') {
    const p = val.pattern;
    const hit = (Object.keys(PRESET_PATTERNS) as PresetKey[]).find((k) => PRESET_PATTERNS[k] === p);
    preset.value = hit ?? 'custom';
    customPattern.value = hit ? '' : p;
  } else if (val.type === 'requiredWhen') {
    reqWhen.fieldKey = val.fieldKey;
    reqWhen.operator = val.operator;
    reqWhen.value = val.value;
    applyInner(val.rule);
  } else if (val.type === 'valueWhen') {
    valueWhenCases.value = (val.cases ?? []).map((c) => ({ ...c }));
  }
}

function applyInner(val: ValueRule | undefined): void {
  if (!val) {
    reqWhen.innerType = 'none';
    return;
  }
  reqWhen.innerType = val.type;
  if (val.type === 'enum') {
    innerEnum.value = [...(val.options ?? [])];
  } else if (val.type === 'codeRange') {
    innerCode.prefix = val.prefix;
    innerCode.start = String(val.start);
    innerCode.end = String(val.end);
    innerCode.width = String(val.width);
  } else if (val.type === 'range') {
    innerRange.min = val.min === undefined ? '' : String(val.min);
    innerRange.max = val.max === undefined ? '' : String(val.max);
  } else if (val.type === 'pattern') {
    innerPattern.value = val.pattern;
  }
}

function toNum(v: string): number | undefined {
  const n = Number(v);
  return v === '' || Number.isNaN(n) ? undefined : n;
}

function addOption(): void {
  enumOptions.value.push('');
}

function removeOption(index: number): void {
  enumOptions.value.splice(index, 1);
}

function addCase(): void {
  valueWhenCases.value.push({ fieldKey: fieldOptions.value[0]?.key ?? '', operator: 'eq', value: '', expect: '' });
}

function removeCase(index: number): void {
  valueWhenCases.value.splice(index, 1);
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
.rule-editor {
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
  width: 100px;
}
.vr-tiny {
  width: 70px;
}
.vr-preset {
  width: 180px;
}
.vr-field {
  width: 160px;
}
.vr-op {
  width: 100px;
}
.vr-pattern {
  width: 260px;
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
.vr-preview__chip--mono {
  font-family: monospace;
  background: var(--brand-bg-secondary);
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  padding: 2px 8px;
}
</style>
