import { createApp, type App as VueApp } from 'vue';
import { createRouter, createWebHistory, type Router } from 'vue-router';
import App from './App.vue';
import { routes } from './router';
import { initTheme } from './theme';
import '@platform/ui-theme/theme.css';

/**
 * 子应用入口（模板）
 * - 保活模式下无需生命周期改造，这里仍实现 __WUJIE_MOUNT/__WUJIE_UNMOUNT 以兼容单例模式与独立运行。
 * - Vite 异步加载，需主动调用 window.__WUJIE.mount()。
 */
let app: VueApp | null = null;
let router: Router | null = null;

function mount(): void {
  router = createRouter({ history: createWebHistory('/'), routes });
  app = createApp(App);
  app.use(router);
  app.mount('#app');
}

function unmount(): void {
  app?.unmount();
  app = null;
  router = null;
}

if (window.__POWERED_BY_WUJIE__) {
  window.__WUJIE_MOUNT = mount;
  window.__WUJIE_UNMOUNT = unmount;
  window.__WUJIE?.mount?.();
} else {
  mount();
}

initTheme();
