import type { RouteRecordRaw } from 'vue-router';

/** 子应用路由（模板：改成你的业务路由） */
export const routes: RouteRecordRaw[] = [
  { path: '/', component: () => import('../views/home/index.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];
