<template>
  <div class="approval-list">
    <h2 class="typography-h2">{{ t('list.title') }}</h2>
    <p class="typography-body page-subtitle">{{ t('list.subtitle') }}</p>

    <!-- 查询区（西门子 iX 组件） -->
    <div class="search-bar">
      <IxInput
        v-model="query.keyword"
        class="search-bar__field"
        :placeholder="t('list.keywordPlaceholder')"
      />
      <IxSelect v-model="query.status" class="search-bar__field search-bar__field--select">
        <IxSelectItem :label="t('list.allStatus')" value="" />
        <IxSelectItem :label="t('list.statusPending')" value="pending" />
        <IxSelectItem :label="t('list.statusApproved')" value="approved" />
        <IxSelectItem :label="t('list.statusRejected')" value="rejected" />
      </IxSelect>
      <IxButton variant="primary" :disabled="loading" @click="onSearch">
        {{ t('list.search') }}
      </IxButton>
      <IxButton @click="onReset">{{ t('list.reset') }}</IxButton>
    </div>

    <!-- 结果表格（iX v5 移除了内置表格，用原生 table + iX 主题变量） -->
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('list.colNo') }}</th>
          <th>{{ t('list.colTitle') }}</th>
          <th>{{ t('list.colApplicant') }}</th>
          <th>{{ t('list.colStatus') }}</th>
          <th>{{ t('list.colTime') }}</th>
          <th>{{ t('list.colAction') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in filteredRows" :key="row.id">
          <td>{{ row.no }}</td>
          <td>{{ row.title }}</td>
          <td>{{ row.applicant }}</td>
          <td>
            <span class="tag" :class="statusClass(row.status)">{{ statusText(row.status) }}</span>
          </td>
          <td>{{ row.time }}</td>
          <td>
            <a class="link" @click="goDetail(row.id)">{{ t('list.view') }}</a>
          </td>
        </tr>
        <tr v-if="filteredRows.length === 0">
          <td colspan="6" class="empty">{{ t('list.empty') }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * 审批列表（查询类页面示例，使用西门子 iX 组件）
 *
 * 说明：
 * 1. 表单/交互用 iX 组件（IxInput / IxSelect / IxButton），布局用品牌 CSS 变量；
 *    iX v5 无表格组件，列表用原生 table + iX 主题变量。
 * 2. 真实接口走 @platform/sdk 的 createSubAppHttp（见 api/approval.ts），后端未启动时回退 mock。
 * 3. 文案走 vue-i18n（中/英/德），语言切换由主应用经 bus 广播同步。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IxButton, IxInput, IxSelect, IxSelectItem } from '@platform/ui-components';
import { fetchApprovalList, type ApprovalRecord } from '../../api/approval';

const { t } = useI18n();

// mock 兜底数据（后端未启动时使用；接好真实后端后可删除）
const MOCK_ROWS: ApprovalRecord[] = [
  { id: '1', no: 'SP-2024-0001', title: '设备维修费用审批', applicant: '张三', status: 'pending', time: '2024-06-01 10:00' },
  { id: '2', no: 'SP-2024-0002', title: '备件采购申请', applicant: '李四', status: 'approved', time: '2024-06-02 14:30' },
  { id: '3', no: 'SP-2024-0003', title: '差旅报销', applicant: '王五', status: 'rejected', time: '2024-06-03 09:15' },
];

// 数据行（初始为空，onMounted 时从接口加载）
const rows = ref<ApprovalRecord[]>([]);
const loading = ref(false);

const query = reactive({ keyword: '', status: '' });
const router = useRouter();

/** 加载列表数据（真实接口 + mock 兜底） */
async function loadData(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await fetchApprovalList({ keyword: query.keyword, status: query.status });
  } catch (e) {
    // 后端未启动时回退 mock，避免白屏（接好真实后端后删除此兜底）
    console.warn('[approval] 接口未就绪，回退 mock 数据', e);
    rows.value = MOCK_ROWS;
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

// 按查询条件过滤（内存过滤，真实场景为服务端过滤）
const filteredRows = computed(() =>
  rows.value.filter((r) => {
    const hitKeyword = !query.keyword || r.no.includes(query.keyword) || r.applicant.includes(query.keyword);
    const hitStatus = !query.status || r.status === query.status;
    return hitKeyword && hitStatus;
  }),
);

function onSearch(): void {
  void loadData();
}

function onReset(): void {
  query.keyword = '';
  query.status = '';
  void loadData();
}

/** 状态文案（i18n：pending/approved/rejected -> list.statusPending/Approved/Rejected） */
function statusText(status: ApprovalRecord['status']): string {
  const key = `list.status${status.charAt(0).toUpperCase()}${status.slice(1)}` as const;
  return t(key);
}

function statusClass(status: ApprovalRecord['status']): string {
  return `tag--${status}`;
}

function goDetail(id: string): void {
  router.push(`/approval/detail/${id}`);
}
</script>

<style scoped>
.approval-list {
  color: var(--brand-text);
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.search-bar__field {
  width: 220px;
}
.search-bar__field--select {
  width: 160px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.table th,
.table td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--brand-border);
  text-align: left;
}
.table th {
  color: var(--brand-text-secondary);
  font-weight: 600;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag--pending {
  background: #fdf3e7;
  color: var(--brand-warning);
}
.tag--approved {
  background: #e8f5ee;
  color: var(--brand-success);
}
.tag--rejected {
  background: #fdecec;
  color: var(--brand-danger);
}
.link {
  color: var(--brand-primary);
  cursor: pointer;
}
.empty {
  text-align: center;
  color: var(--brand-text-disabled);
  padding: 24px 0;
}
</style>
