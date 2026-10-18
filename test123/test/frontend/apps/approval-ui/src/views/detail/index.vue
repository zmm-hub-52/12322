<template>
  <div class="approval-detail">
    <div class="page-head">
      <a class="link" @click="router.push('/approval/list')">← {{ t('detail.back') }}</a>
      <h2 class="typography-h2 page-title">{{ t('detail.title') }}</h2>
      <span v-if="schema" class="page-node">
        {{ t('detail.currentNode') }}：{{ schema.nodeName ?? schema.nodeKey }}
      </span>
    </div>

    <!-- 加载中 -->
    <div v-if="!schema && !error" class="empty">{{ t('detail.loading') }}</div>
    <p v-if="error" class="empty">{{ error }}</p>

    <template v-if="schema">
      <!-- 已结束 -->
      <div v-if="schema.finished" class="finished">
        {{ t('detail.finished') }}
        <span v-if="schema.endActivityId?.includes('rejEnd')" class="finished__reason">
          （{{ t('detail.terminated') }}）
        </span>
      </div>

      <!-- 表单 -->
      <div v-if="schema.fields && schema.fields.length" class="panel">
        <FormRenderer
          :ref="(el) => setFormRef(el)"
          v-model="formData"
          :fields="schema.fields"
          :readonly="schema.nodeType !== 'fill' || schema.finished"
        />
      </div>

      <!-- 审批操作区 -->
      <div v-if="schema.nodeType === 'approval' && !schema.finished" class="panel ops">
        <label class="ops__label">{{ t('detail.comment') }}</label>
        <textarea v-model="comment" class="ops__comment" :placeholder="t('detail.commentPh')"></textarea>
        <div class="ops__btns">
          <IxButton variant="primary" :disabled="acting" @click="doApprove(true)">
            {{ t('detail.approve') }}
          </IxButton>
          <IxButton :disabled="acting" @click="doApprove(false)">
            {{ t('detail.reject') }}
          </IxButton>
        </div>
      </div>

      <!-- 填写节点操作区 -->
      <div v-if="schema.nodeType === 'fill' && !schema.finished" class="panel ops">
        <IxButton variant="primary" :disabled="acting" @click="doSubmit">
          {{ t('detail.submit') }}
        </IxButton>
      </div>

      <!-- 轨迹时间线 -->
      <div class="panel">
        <h3 class="section-title">{{ t('detail.records') }}</h3>
        <div v-if="records.length === 0" class="empty">{{ t('detail.noRecords') }}</div>
        <div v-for="r in records" :key="r.id" class="record">
          <span class="record__action" :class="`record__action--${r.actionType}`">{{ actionText(r.actionType) }}</span>
          <span class="record__node">{{ r.nodeName ?? r.nodeKey }}</span>
          <span class="record__executor">{{ r.executor ?? '—' }}</span>
          <span class="record__comment">{{ r.comment ?? '' }}</span>
          <span class="record__time">{{ formatTime(r.createTime) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 办理页：渲染当前节点表单（fill 可编辑提交 / approval 只读+通过驳回）+ 轨迹时间线。
 */
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton } from '@platform/ui-components';
import FormRenderer from '../../components/FormRenderer.vue';
import { approveTask, fetchNodeSchema, fetchRecords, submitTask } from '../../api';
import { getFeedback } from '@platform/sdk';
import type { NodeSchema, RecordView } from '../../types/schema';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const { showToast } = getFeedback();

const instanceId = Number(route.params.id);
const schema = ref<NodeSchema | null>(null);
const records = ref<RecordView[]>([]);
const formData = ref<Record<string, unknown>>({});
const comment = ref('');
const acting = ref(false);
const error = ref('');
let formRef: InstanceType<typeof FormRenderer> | null = null;

function setFormRef(el: unknown): void {
  formRef = (el as InstanceType<typeof FormRenderer>) ?? null;
}

async function load(): Promise<void> {
  try {
    const [s, recs] = await Promise.all([fetchNodeSchema(instanceId), fetchRecords(instanceId)]);
    schema.value = s;
    formData.value = { ...(s.formData ?? {}) };
    records.value = recs;
  } catch (e) {
    error.value = (e as Error).message;
  }
}

onMounted(load);

async function doSubmit(): Promise<void> {
  if (!schema.value?.taskId) return;
  if (formRef && !formRef.validate()) return;
  acting.value = true;
  try {
    await submitTask(schema.value.taskId, { ...formData.value });
    showToast({ message: t('detail.submitted') });
    comment.value = '';
    await load();
  } catch (e) {
    showToast({ message: (e as Error).message });
  } finally {
    acting.value = false;
  }
}

async function doApprove(approved: boolean): Promise<void> {
  if (!schema.value?.taskId) return;
  acting.value = true;
  try {
    await approveTask(schema.value.taskId, approved, comment.value || undefined);
    showToast({ message: approved ? t('detail.approved') : t('detail.rejected') });
    comment.value = '';
    await load();
  } catch (e) {
    showToast({ message: (e as Error).message });
  } finally {
    acting.value = false;
  }
}

function actionText(action: string): string {
  const map: Record<string, string> = {
    start: t('detail.actStart'),
    submit: t('detail.actSubmit'),
    approve: t('detail.actApprove'),
    reject: t('detail.actReject'),
    terminate: t('detail.actTerminate'),
  };
  return map[action] ?? action;
}

function formatTime(v: string): string {
  return (v ?? '').replace('T', ' ').slice(0, 19);
}
</script>

<style scoped>
.approval-detail {
  color: var(--brand-text);
}
.page-head {
  margin-bottom: 16px;
}
.page-title {
  margin: 4px 0 0;
}
.page-node {
  font-size: 13px;
  color: var(--brand-warning);
}
.panel {
  max-width: 720px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-lg);
  background: var(--brand-bg-secondary);
  padding: 16px;
  margin-bottom: 16px;
}
.finished {
  max-width: 720px;
  padding: 16px;
  text-align: center;
  color: var(--brand-success);
  font-weight: 600;
  border: 1px dashed var(--brand-success);
  border-radius: var(--brand-radius-lg);
  margin-bottom: 16px;
}
.finished__reason {
  color: var(--brand-warning);
  font-weight: 400;
}
.ops {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ops__label {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.ops__comment {
  min-height: 72px;
  padding: 8px 10px;
  border: 1px solid var(--brand-border-strong);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  color: var(--brand-text);
  font-family: inherit;
  resize: vertical;
}
.ops__btns {
  display: flex;
  gap: 12px;
}
.section-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--brand-primary);
}
.record {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--brand-border);
  font-size: 13px;
}
.record__action {
  width: 72px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  text-align: center;
  background: var(--brand-bg-tertiary);
  color: var(--brand-text-secondary);
}
.record__action--reject,
.record__action--terminate {
  background: color-mix(in srgb, var(--brand-warning) 15%, var(--brand-bg));
  color: var(--brand-warning);
}
.record__action--approve {
  background: color-mix(in srgb, var(--brand-success) 15%, var(--brand-bg));
  color: var(--brand-success);
}
.record__node {
  font-weight: 600;
}
.record__executor {
  color: var(--brand-text-secondary);
}
.record__comment {
  flex: 1;
  color: var(--brand-text-secondary);
}
.record__time {
  color: var(--brand-text-disabled);
  font-size: 12px;
  white-space: nowrap;
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
  font-size: 13px;
}
.empty {
  color: var(--brand-text-disabled);
  text-align: center;
  padding: 24px 0;
}
</style>
