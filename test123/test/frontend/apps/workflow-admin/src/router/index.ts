import type { RouteRecordRaw } from 'vue-router';

/**
 * workflow-admin 子应用路由表
 * 说明：history 模式 + 无界路由同步，路由以 /workflow-admin 开头；
 *       部署时子应用 base 需与主应用菜单配置的 url 对齐。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/workflow-admin/process',
    name: 'ProcessList',
    component: () => import('../views/process/list.vue'),
    meta: { title: '流程配置' },
  },
  {
    path: '/workflow-admin/process/wizard',
    name: 'ProcessWizard',
    component: () => import('../views/process/wizard.vue'),
    meta: { title: '流程配置向导' },
  },
  {
    path: '/workflow-admin/form',
    name: 'FormConfig',
    component: () => import('../views/form/index.vue'),
    meta: { title: '动态表单配置' },
  },
  {
    path: '/workflow-admin/dimension',
    name: 'DimensionConfig',
    component: () => import('../views/dimension/index.vue'),
    meta: { title: '维表配置' },
  },
  // 兜底：未知路由回流程配置列表
  { path: '/:pathMatch(.*)*', redirect: '/workflow-admin/process' },
];
