import type { RouteRecordRaw } from 'vue-router';

/**
 * 子应用路由表
 * 说明：history 模式 + 无界路由同步，路由以 /approval 开头；
 *       部署时子应用 base 需与主应用菜单配置的 url 对齐。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/approval/start',
    name: 'ApprovalStart',
    component: () => import('../views/start/index.vue'),
    meta: { title: '发起流程' },
  },
  {
    path: '/approval/list',
    name: 'ApprovalList',
    component: () => import('../views/list/index.vue'),
    meta: { title: '我的待办' },
  },
  {
    path: '/approval/detail/:id?',
    name: 'ApprovalDetail',
    component: () => import('../views/detail/index.vue'),
    meta: { title: '办理' },
  },
  // 兜底：未知路由回列表页
  { path: '/:pathMatch(.*)*', redirect: '/approval/list' },
];
