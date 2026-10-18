import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { STORAGE_KEYS } from '@platform/sdk';

/**
 * 主应用路由
 * 说明：子应用不注册为主应用路由，而是通过「菜单 -> 多 Tab -> 无界 startApp」的方式挂载；
 *       主应用路由只负责壳（登录页 + 默认布局下的首页）。
 */
const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('../views/login/index.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('../views/home/index.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory('/'),
  routes,
});

/**
 * 全局前置守卫：鉴权占位。
 * 真实场景：校验 token 有效性，未登录跳 /login，并按用户角色做路由级权限过滤。
 * 目前仅做「无 token 跳登录」的简单判断。
 */
router.beforeEach((to) => {
  if (to.meta.public) return true;
  const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
  if (!token) return { path: '/login' };
  return true;
});

export default router;
