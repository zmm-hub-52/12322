<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { IxButton, IxSelect, IxSelectItem, showToast } from '@siemens/ix-vue'
import {
  ORDER_PRIORITY_COLOR,
  ORDER_PRIORITY_LABEL,
  ORDER_STATUS_LABEL,
  ORDER_STATUS_VARIANT,
  mockOrders,
} from '@/mocks/orders'
import type { OrderStatus } from '@/mocks/orders'

// ---- 筛选 & 分页状态 ----
const statusFilter = ref<'all' | OrderStatus>('all')
const pageSize = 5
const page = ref(0)

const filtered = computed(() =>
  statusFilter.value === 'all'
    ? mockOrders
    : mockOrders.filter((o) => o.status === statusFilter.value),
)

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const pageData = computed(() =>
  filtered.value.slice(page.value * pageSize, (page.value + 1) * pageSize),
)

watch(statusFilter, () => {
  page.value = 0
})

// ---- 状态选项 ----
const statusOptions: Array<{ label: string; value: 'all' | OrderStatus }> = [
  { label: '全部状态', value: 'all' },
  ...(Object.keys(ORDER_STATUS_LABEL) as OrderStatus[]).map((s) => ({
    label: ORDER_STATUS_LABEL[s],
    value: s,
  })),
]

function onPageSelected(selected: number) {
  page.value = selected
}

function onDetail(id: string) {
  showToast({ title: '查看详情', message: `打开报修单 ${id}`, type: 'info' })
}

function onAccept(id: string) {
  showToast({ title: '接单成功', message: `已接单 ${id}`, type: 'success' })
}
</script>

<template>
  <div class="page">
    <div class="demo-head">
      <div>
        <h1 class="page-title">方案 A · 原生表格 + iX 周边组件</h1>
        <p class="page-desc">
          用原生 <code>&lt;table&gt;</code> 搭骨架，配合 iX 的筛选、状态 pill、分页、空态组件拼装。零额外依赖。
        </p>
      </div>
      <div class="filter">
        <span class="filter-label">状态筛选：</span>
        <IxSelect v-model="statusFilter" style="min-width: 180px">
          <IxSelectItem
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </IxSelect>
      </div>
    </div>

    <!-- 空态 -->
    <ix-empty-state
      v-if="pageData.length === 0"
      header="暂无报修单"
      sub-header="当前筛选条件下没有数据，试试切换状态筛选"
    />

    <!-- 表格 -->
    <div v-else class="table-wrap">
      <table class="order-table">
        <thead>
          <tr>
            <th>编号</th>
            <th>标题</th>
            <th>分类</th>
            <th>报修人</th>
            <th>优先级</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in pageData" :key="o.id">
            <td class="mono">{{ o.id }}</td>
            <td class="title">{{ o.title }}</td>
            <td>{{ o.category }}</td>
            <td>{{ o.reporter }}</td>
            <td>
              <span class="prio">
                <span class="prio-dot" :style="{ background: ORDER_PRIORITY_COLOR[o.priority] }" />
                {{ ORDER_PRIORITY_LABEL[o.priority] }}
              </span>
            </td>
            <td>
              <ix-pill :variant="ORDER_STATUS_VARIANT[o.status]">
                {{ ORDER_STATUS_LABEL[o.status] }}
              </ix-pill>
            </td>
            <td class="mono muted">{{ o.createdAt }}</td>
            <td>
              <div class="actions">
                <IxButton variant="tertiary" @click="onDetail(o.id)">详情</IxButton>
                <IxButton
                  v-if="o.status === 'pending'"
                  variant="primary"
                  @click="onAccept(o.id)"
                >
                  接单
                </IxButton>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="table-footer">
        <span class="muted">共 {{ filtered.length }} 条</span>
        <ix-pagination
          :count="totalPages"
          :selected-page="page"
          @pageSelected="onPageSelected"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.demo-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.page-desc {
  color: var(--theme-color-soft-text, #666);
  margin: 0;
}

.filter {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-label {
  color: var(--theme-color-soft-text, #666);
  white-space: nowrap;
}

.table-wrap {
  border: 1px solid var(--theme-color-weak-bdr, #e0e0e0);
  border-radius: 8px;
  background: var(--theme-color-1, #fff);
  overflow: hidden;
}

.order-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.order-table th,
.order-table td {
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--theme-color-x-weak-bdr, #f0f0f0);
  vertical-align: middle;
}

.order-table thead th {
  background: var(--theme-color-2, #f5f6f7);
  color: var(--theme-color-soft-text, #555);
  font-weight: 600;
  white-space: nowrap;
}

.order-table tbody tr:hover {
  background: var(--theme-color-ghost--hover, #f7f8f9);
}

.order-table tbody tr:last-child td {
  border-bottom: none;
}

.title {
  font-weight: 500;
  color: var(--theme-color-std-text, #222);
}

.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 0.8rem;
}

.muted {
  color: var(--theme-color-weak-text, #999);
}

.prio {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.prio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--theme-color-x-weak-bdr, #f0f0f0);
}
</style>
