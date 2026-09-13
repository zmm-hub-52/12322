<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  AllCommunityModule,
  ModuleRegistry,
  createGrid,
  createPart,
  themeAlpine,
} from 'ag-grid-community'
import type { ColDef, GridApi } from 'ag-grid-community'
import { getIxTheme } from '@siemens/ix-aggrid'
import {
  ORDER_PRIORITY_COLOR,
  ORDER_PRIORITY_LABEL,
  ORDER_STATUS_LABEL,
  ORDER_STATUS_VARIANT,
  mockOrders,
} from '@/mocks/orders'
import type { OrderPriority, OrderStatus } from '@/mocks/orders'

// 注册 AG Grid 社区版全部模块
ModuleRegistry.registerModules([AllCommunityModule])

// 基于 ix 主题变量构建 AG Grid 主题（官方 @siemens/ix-aggrid）
const ixTheme = getIxTheme({ createPart, themeAlpine }, { stripedRows: true })

const gridEl = ref<HTMLDivElement>()
let gridApi: GridApi | undefined

// 状态列：复用 ix-pill 渲染，保持与方案 A 一致
const statusRenderer = (params: { value: OrderStatus }) =>
  `<ix-pill variant="${ORDER_STATUS_VARIANT[params.value]}">${ORDER_STATUS_LABEL[params.value]}</ix-pill>`

// 优先级列：着色文本
const priorityRenderer = (params: { value: OrderPriority }) =>
  `<span style="font-weight:600;color:${ORDER_PRIORITY_COLOR[params.value]}">${ORDER_PRIORITY_LABEL[params.value]}</span>`

const columnDefs: ColDef[] = [
  { field: 'id', headerName: '编号', width: 150 },
  { field: 'title', headerName: '标题', flex: 1, minWidth: 220 },
  { field: 'category', headerName: '分类', width: 110 },
  { field: 'reporter', headerName: '报修人', width: 100 },
  { field: 'priority', headerName: '优先级', width: 90, cellRenderer: priorityRenderer },
  { field: 'status', headerName: '状态', width: 120, cellRenderer: statusRenderer },
  { field: 'createdAt', headerName: '创建时间', width: 165, sort: 'desc' },
  { field: 'assignee', headerName: '处理人', width: 100 },
]

onMounted(() => {
  gridApi = createGrid(gridEl.value!, {
    theme: ixTheme,
    columnDefs,
    rowData: mockOrders,
    pagination: true,
    paginationPageSize: 8,
    paginationPageSizeSelector: [8, 15, 30],
    defaultColDef: {
      sortable: true,
      filter: true,
      resizable: true,
    },
    rowHeight: 42,
  })
})

onBeforeUnmount(() => {
  gridApi?.destroy()
  gridApi = undefined
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">方案 B · AG Grid + @siemens/ix-aggrid 主题</h1>
    <p class="page-desc">
      直接使用 AG Grid 社区版，套上 iX 官方主题（getIxTheme）。排序 / 筛选 / 列宽调整 / 分页开箱即用。
    </p>

    <div ref="gridEl" class="grid-host" />
  </div>
</template>

<style scoped>
.page-desc {
  color: var(--theme-color-soft-text, #666);
  margin: 0 0 1rem;
}

.grid-host {
  width: 100%;
  height: 560px;
  border: 1px solid var(--theme-color-weak-bdr, #e0e0e0);
  border-radius: 8px;
  overflow: hidden;
}
</style>
