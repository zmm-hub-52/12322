import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由配置。
 * 报修业务页面（报修单列表、详情、工作台等）后续在 children 中扩展。
 * 登录守卫见底部 beforeEach。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/login/callback',
      name: 'login-callback',
      component: () => import('@/views/LoginCallbackView.vue'),
      meta: { title: '登录中', public: true },
    },
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

  const authStore = useAuthStore()

  // 未登录访问受保护页面 → 跳登录页，并记录原目标
  if (!to.meta.public && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 已登录访问登录页 → 跳首页
  if (to.name === 'login' && authStore.isLoggedIn) {
    return { name: 'home' }
  }

  return true
})

export default router
