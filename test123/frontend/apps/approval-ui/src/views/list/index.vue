<template>
  <div class="approval-list">
    <PageHeader title="审批列表" subtitle="审批中心 · 查询类页面示例" />

    <!-- 查询区 -->
    <form class="search-bar" @submit.prevent="onSearch">
      <input v-model="query.keyword" class="field" type="text" placeholder="单号 / 申请人" />
      <select v-model="query.status" class="field">
        <option value="">全部状态</option>
        <option value="pending">待审批</option>
        <option value="approved">已通过</option>
        <option value="rejected">已驳回</option>
      </select>
      <button type="submit" class="btn btn--primary">查询</button>
      <button type="button" class="btn" @click="onReset">重置</button>
    </form>

    <!-- 结果表格（原生 table + 品牌 CSS 变量） -->
    <table class="table">
      <thead>
        <tr>
          <th>单号</th>
          <th>标题</th>
          <th>申请人</th>
          <th>状态</th>
          <th>申请时间</th>
          <th>操作</th>
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
            <a class="link" @click="goDetail(row.id)">查看</a>
          </td>
        </tr>
        <tr v-if="filteredRows.length === 0">
          <td colspan="6" class="empty">暂无数据</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * 审批列表（查询类页面示例）
 *
 * 说明：
 * 1. 本页为「查询类页面」，在保活模式下若需要激活时重新拉取数据，
 *    可在无界的 activated 生命周期（startApp 参数）或 bus 事件中触发刷新；
 *    当前平台采用「10 分钟超时销毁 → 重新加载」策略，数据天然刷新。
 * 2. 真实接口应走 @platform/sdk 的 createHttp 统一客户端，这里用 mock 数据演示。
 */
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { PageHeader } from '@platform/ui-components';

interface ApprovalRow {
  id: string;
  no: string;
  title: string;
  applicant: string;
  status: 'pending' | 'approved' | 'rejected';
  time: string;
}

// mock 数据（真实场景替换为 sdk 接口返回）
const rows = ref<ApprovalRow[]>([
  { id: '1', no: 'SP-2024-0001', title: '设备维修费用审批', applicant: '张三', status: 'pending', time: '2024-06-01 10:00' },
  { id: '2', no: 'SP-2024-0002', title: '备件采购申请', applicant: '李四', status: 'approved', time: '2024-06-02 14:30' },
  { id: '3', no: 'SP-2024-0003', title: '差旅报销', applicant: '王五', status: 'rejected', time: '2024-06-03 09:15' },
]);

const query = reactive({ keyword: '', status: '' });
const router = useRouter();

// 按查询条件过滤（内存过滤，真实场景为服务端过滤）
const filteredRows = computed(() =>
  rows.value.filter((r) => {
    const hitKeyword = !query.keyword || r.no.includes(query.keyword) || r.applicant.includes(query.keyword);
    const hitStatus = !query.status || r.status === query.status;
    return hitKeyword && hitStatus;
  }),
);

function onSearch(): void {
  // 真实场景：调用 sdk 接口，携带 query 查询
}

function onReset(): void {
  query.keyword = '';
  query.status = '';
}

function statusText(status: ApprovalRow['status']): string {
  return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[status];
}

function statusClass(status: ApprovalRow['status']): string {
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
  gap: 8px;
  margin-bottom: 12px;
}
.field {
  padding: 6px 8px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg);
  color: var(--brand-text);
}
.btn {
  padding: 6px 12px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg-secondary);
  color: var(--brand-text);
  cursor: pointer;
}
.btn--primary {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
  color: #fff;
}
.btn--primary:hover {
  background: var(--brand-primary-hover);
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
