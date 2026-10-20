<template>
  <div class="dimension">
    <div class="page-head">
      <h2 class="typography-h2">{{ t('dimension.title') }}</h2>
      <p class="typography-body page-subtitle">{{ t('dimension.subtitle') }}</p>
    </div>

    <!-- 三栏主从：维度 → 分类 → 字段（字段即第三层，配置按钮配字段属性与规则） -->
    <div class="three-col">
      <!-- 维度 -->
      <div class="col">
        <div class="col__head">{{ t('dimension.dimensionList') }}</div>
        <div class="col__body">
          <div
            v-for="d in dimensions"
            :key="d.id"
            class="col-row"
            :class="{ 'col-row--active': d.id === selectedDimensionId }"
            @click="selectDimension(d)"
          >
            <span class="col-row__name">{{ d.dimensionName }}</span>
            <span class="col-row__code mono">{{ d.dimensionCode }}</span>
          </div>
          <p v-if="!dimensions.length" class="col__empty">{{ t('dimension.empty') }}</p>
        </div>
        <div class="col__add">
          <IxInput v-model="newDimension.name" :placeholder="t('dimension.dimensionNamePh')" class="col__add-input" />
          <IxInput v-model="newDimension.code" :placeholder="t('dimension.dimensionCodePh')" class="col__add-input col__add-input--small" />
          <IxButton variant="secondary" @click="addDimension">{{ t('dimension.addDimension') }}</IxButton>
        </div>
      </div>

      <!-- 分类 -->
      <div class="col">
        <div class="col__head">{{ t('dimension.categoryList') }}</div>
        <div class="col__body">
          <template v-if="selectedDimensionId">
            <div
              v-for="c in categories"
              :key="c.id"
              class="col-row"
              :class="{ 'col-row--active': c.id === selectedCategoryId }"
              @click="selectCategory(c)"
            >
              <span class="col-row__name">{{ c.categoryName }}</span>
              <span class="col-row__code mono">{{ c.categoryCode }}</span>
            </div>
            <p v-if="!categories.length" class="col__empty">{{ t('dimension.empty') }}</p>
          </template>
          <p v-else class="col__empty">{{ t('dimension.selectHint') }}</p>
        </div>
        <div class="col__add">
          <IxInput v-model="newCategory.name" :placeholder="t('dimension.categoryNamePh')" class="col__add-input" />
          <IxInput v-model="newCategory.code" :placeholder="t('dimension.categoryCodePh')" class="col__add-input col__add-input--small" />
          <IxButton variant="secondary" :disabled="!selectedDimensionId" @click="addCategory">
            {{ t('dimension.addCategory') }}
          </IxButton>
        </div>
      </div>

      <!-- 字段（第三层）：每行一个「配置」按钮 -->
      <div class="col col--wide">
        <div class="col__head">
          {{ t('dimension.fieldList') }}
          <IxButton
            v-if="selectedCategoryId"
            variant="secondary"
            class="col__head-btn"
            @click="openCreate"
          >
            {{ t('dimension.addField') }}
          </IxButton>
        </div>
        <div class="col__body">
          <template v-if="selectedCategoryId">
            <table class="table">
              <thead>
                <tr>
                  <th>{{ t('dimension.colKey') }}</th>
                  <th>{{ t('dimension.colName') }}</th>
                  <th>{{ t('dimension.colType') }}</th>
                  <th>{{ t('dimension.colRequired') }}</th>
                  <th>{{ t('dimension.colAction') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="f in fields" :key="f.fieldKey">
                  <td class="mono">{{ f.fieldKey }}</td>
                  <td>{{ f.fieldName }}</td>
                  <td><span class="tag tag--type">{{ typeText(f.fieldType) }}</span></td>
                  <td>{{ f.required ? t('field.yes') : t('field.no') }}</td>
                  <td class="item-actions">
                    <a class="link" @click="openEdit(f)">{{ t('dimension.config') }}</a>
                    <a class="link" @click="onDeleteField(f)">{{ t('dimension.disabled') }}</a>
                  </td>
                </tr>
                <tr v-if="!fields.length">
                  <td colspan="5" class="col__empty">{{ t('dimension.fieldEmpty') }}</td>
                </tr>
              </tbody>
            </table>
          </template>
          <p v-else class="col__empty">{{ t('dimension.selectCategoryHint') }}</p>
        </div>
      </div>
    </div>

    <!-- 字段配置弹层 -->
    <div v-if="modal" class="modal-mask" @click.self="modal = null">
      <div class="modal" @click.stop>
        <h3 class="modal__title">{{ modal.editingId ? t('dimension.editField') : t('dimension.addField') }}</h3>
        <div class="modal__grid">
          <label class="modal__field">
            <span class="modal__label">{{ t('dimension.colKey') }}</span>
            <IxInput v-model="form.fieldKey" :disabled="!!modal.editingId" :placeholder="t('dimension.keyPh')" />
          </label>
          <label class="modal__field">
            <span class="modal__label">{{ t('dimension.colName') }}</span>
            <IxInput v-model="form.fieldName" :placeholder="t('dimension.namePh')" />
          </label>
          <label class="modal__field modal__field--wide">
            <span class="modal__label">{{ t('dimension.colDesc') }}</span>
            <IxInput v-model="form.description" :placeholder="t('dimension.descPh')" />
          </label>
          <label class="modal__field">
            <span class="modal__label">{{ t('dimension.colType') }}</span>
            <IxSelect v-model="form.fieldType">
              <IxSelectItem :label="t('field.typeInput')" value="input" />
              <IxSelectItem :label="t('field.typeTextarea')" value="textarea" />
              <IxSelectItem :label="t('field.typeNumber')" value="number" />
              <IxSelectItem :label="t('field.typeSelect')" value="select" />
              <IxSelectItem :label="t('field.typeDate')" value="date" />
            </IxSelect>
          </label>
          <label class="modal__field">
            <span class="modal__label">{{ t('dimension.colRequired') }}</span>
            <IxToggle :checked="form.required" @checked-change="form.required = $event.detail" />
          </label>
        </div>

        <div class="modal__block">
          <RuleEditor v-model="form.rule" />
        </div>

        <div class="modal__foot">
          <IxButton variant="secondary" @click="modal = null">{{ t('common.cancel') }}</IxButton>
          <IxButton variant="primary" :disabled="!canSave" @click="onSave">{{ t('common.ok') }}</IxButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 维表配置页（v3：三栏主从 = 维度 → 分类 → 字段）。
 * 字段就是第三层：每行「配置」按钮弹层配字段属性（类型/必填/取值规则/校验），
 * 下拉字段的选项由运维在取值规则里手填；字段上维护默认规则，节点配置时继承可覆盖。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem, IxToggle } from '@platform/ui-components';
import RuleEditor from '../../components/RuleEditor.vue';
import {
  createCategory,
  createDimension,
  createField,
  deleteField,
  fetchCategories,
  fetchDimensions,
  fetchFields,
  updateField,
} from '../../mock/service';
import { toastMsg } from '../../utils/feedback';
import type { Dimension, DimensionCategory, FieldDef } from '../../types/schema';

const { t } = useI18n();

const dimensions = ref<Dimension[]>([]);
const categories = ref<DimensionCategory[]>([]);
const fields = ref<FieldDef[]>([]);

const selectedDimensionId = ref<number | null>(null);
const selectedCategoryId = ref<number | null>(null);

const newDimension = reactive({ name: '', code: '' });
const newCategory = reactive({ name: '', code: '' });

/** 字段配置弹层：null 关闭；editingId=null 新增 */
const modal = ref<{ editingId: number | null } | null>(null);

const form = reactive<{
  fieldKey: string;
  fieldName: string;
  description: string;
  fieldType: FieldDef['fieldType'];
  required: boolean;
  rule?: FieldDef['rule'];
}>({
  fieldKey: '',
  fieldName: '',
  description: '',
  fieldType: 'input',
  required: false,
  rule: undefined,
});

onMounted(async () => {
  dimensions.value = await fetchDimensions();
  if (dimensions.value.length) {
    await selectDimension(dimensions.value[0]);
  }
});

/** 选中维度：加载分类并默认选中第一个 */
async function selectDimension(d: Dimension): Promise<void> {
  selectedDimensionId.value = d.id;
  categories.value = await fetchCategories(d.id);
  if (categories.value.length) {
    await selectCategory(categories.value[0]);
  } else {
    selectedCategoryId.value = null;
    fields.value = [];
  }
}

/** 选中分类：加载分类下的字段 */
async function selectCategory(c: DimensionCategory): Promise<void> {
  selectedCategoryId.value = c.id;
  fields.value = await fetchFields(c.id);
}

// ---- 新增维度 / 分类（真实接口 + 刷新） ----
async function addDimension(): Promise<void> {
  if (!newDimension.name.trim() || !newDimension.code.trim()) {
    toastMsg(t('dimension.fillAll'));
    return;
  }
  try {
    await createDimension(newDimension.code.trim(), newDimension.name.trim());
    newDimension.name = '';
    newDimension.code = '';
    dimensions.value = await fetchDimensions();
  } catch (e) {
    toastMsg((e as Error).message);
  }
}

async function addCategory(): Promise<void> {
  if (!newCategory.name.trim() || !newCategory.code.trim()) {
    toastMsg(t('dimension.fillAll'));
    return;
  }
  if (!selectedDimensionId.value) return;
  try {
    await createCategory(selectedDimensionId.value, newCategory.code.trim(), newCategory.name.trim());
    newCategory.name = '';
    newCategory.code = '';
    categories.value = await fetchCategories(selectedDimensionId.value);
  } catch (e) {
    toastMsg((e as Error).message);
  }
}

// ---- 字段配置弹层 ----
const canSave = computed(() => {
  if (!form.fieldKey.trim() || !form.fieldName.trim()) return false;
  return true;
});

function openCreate(): void {
  form.fieldKey = '';
  form.fieldName = '';
  form.description = '';
  form.fieldType = 'input';
  form.required = false;
  form.rule = undefined;
  modal.value = { editingId: null };
}

function openEdit(f: FieldDef): void {
  form.fieldKey = f.fieldKey;
  form.fieldName = f.fieldName;
  form.description = f.description ?? '';
  form.fieldType = f.fieldType;
  form.required = f.required;
  form.rule = f.rule;
  modal.value = { editingId: f.id };
}

async function onSave(): Promise<void> {
  try {
    if (modal.value?.editingId) {
      await updateField(modal.value.editingId, {
        fieldName: form.fieldName.trim(),
        description: form.description.trim(),
        fieldType: form.fieldType,
        required: form.required,
        rule: form.rule,
      });
    } else if (selectedCategoryId.value) {
      await createField(selectedCategoryId.value, {
        fieldKey: form.fieldKey.trim(),
        fieldName: form.fieldName.trim(),
        description: form.description.trim(),
        fieldType: form.fieldType,
        required: form.required,
        rule: form.rule,
      });
    }
    modal.value = null;
    if (selectedCategoryId.value) {
      fields.value = await fetchFields(selectedCategoryId.value);
    }
  } catch (e) {
    toastMsg((e as Error).message);
  }
}

/** 停用字段（逻辑删除，节点引用不失效） */
async function onDeleteField(f: FieldDef): Promise<void> {
  try {
    await deleteField(f.id);
    fields.value = fields.value.filter((x) => x.id !== f.id);
  } catch (e) {
    toastMsg((e as Error).message);
  }
}

function typeText(type: FieldDef['fieldType']): string {
  const map: Record<string, string> = {
    input: 'field.typeInput',
    textarea: 'field.typeTextarea',
    number: 'field.typeNumber',
    select: 'field.typeSelect',
    date: 'field.typeDate',
  };
  return t(map[type] ?? 'field.typeInput');
}
</script>

<style scoped>
.dimension {
  color: var(--brand-text);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.page-head {
  margin-bottom: 16px;
  flex-shrink: 0;
}
.page-subtitle {
  color: var(--brand-text-secondary);
  margin-top: 4px;
}

.three-col {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}
.col {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.col--wide {
  flex: 1;
  width: auto;
}
.col__head {
  padding: 10px 12px;
  border-bottom: 1px solid var(--brand-border);
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.col__head-btn {
  font-weight: 400;
}
.col__body {
  flex: 1;
  overflow: auto;
  min-height: 0;
}
.col-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--brand-border);
  cursor: pointer;
  font-size: 13px;
}
.col-row:hover {
  background: var(--brand-bg-tertiary);
}
.col-row--active {
  background: color-mix(in srgb, var(--brand-primary) 10%, var(--brand-bg));
  border-left: 3px solid var(--brand-primary);
}
.col-row__code {
  font-size: 12px;
  color: var(--brand-text-secondary);
}
.col__empty {
  padding: 24px 12px;
  color: var(--brand-text-disabled);
  font-size: 13px;
  text-align: center;
}
.col__add {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--brand-border);
  flex-wrap: wrap;
}
.col__add-input {
  flex: 1;
  min-width: 90px;
}
.col__add-input--small {
  flex: 0 0 100px;
}
.mono {
  font-family: monospace;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.table th,
.table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--brand-border);
  text-align: left;
}
.table th {
  color: var(--brand-text-secondary);
  font-weight: 600;
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
.item-actions {
  white-space: nowrap;
}
.item-actions .link {
  margin-right: 12px;
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}

/* ---- 配置弹层 ---- */
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
  width: 560px;
  max-height: 86vh;
  overflow: auto;
  background: var(--brand-bg);
  border-radius: var(--brand-radius-lg);
  box-shadow: var(--brand-shadow-lg);
  padding: 20px;
}
.modal__title {
  margin: 0 0 16px;
}
.modal__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}
.modal__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.modal__field--wide {
  grid-column: 1 / -1;
}
.modal__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.modal__block {
  border-left: 3px solid var(--brand-border-strong);
  padding: 8px 12px;
  margin-bottom: 12px;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}
</style>
