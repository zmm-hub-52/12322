import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

/**
 * 路由配置。
 * 报修业务页面（报修单列表、详情、工作台等）后续在 children 中扩展。
 * 角色权限守卫可在 beforeEach 中接入（见底部示例）。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: '报修单' },
        },
        {
          path: 'demo/native',
          name: 'demo-native',
          component: () => import('@/views/demo/OrderTableNative.vue'),
          meta: { title: '方案A · 原生表格' },
        },
        {
          path: 'demo/aggrid',
          name: 'demo-aggrid',
          component: () => import('@/views/demo/OrderTableAgGrid.vue'),
          meta: { title: '方案B · AG Grid' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  document.title = `${String(to.meta.title || '首页')} - ${
    import.meta.env.VITE_APP_TITLE || '报修系统'
  }`
  // 角色权限守卫示例（业务阶段启用）：
  // if (to.meta.roles && !to.meta.roles.includes(useAuthStore().role)) return { name: 'home' }
  return true
})

export default router
