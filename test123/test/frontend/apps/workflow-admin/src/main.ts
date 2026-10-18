import { createApp, type App as VueApp } from 'vue';
import { createPinia } from 'pinia';
import { createRouter, createWebHistory, type Router } from 'vue-router';
import App from './App.vue';
import { routes } from './router';
import { initTheme } from './theme';
import { i18n, initLocale } from './i18n';
import { defineCustomElements } from '@siemens/ix/loader';
import { defineCustomElements as defineIxIcons } from '@siemens/ix-icons/loader';
import '@platform/ui-theme/theme.css';

/**
 * workflow-admin 子应用入口
 *
 * 无界子应用运行模式说明：
 * - 本平台统一采用「保活模式」（主应用 startApp 时 alive: true），
 *   保活模式下子应用「无需做生命周期改造」，这里仍实现 __WUJIE_MOUNT/__WUJIE_UNMOUNT，
 *   是为将来切换到「单例模式」预留，同时保证子应用可独立运行。
 * - Vite 是异步加载，必须在定义完生命周期后主动调用 window.__WUJIE.mount()，
 *   否则无界可能在 vite 尚未加载完成时就调用 mount 导致挂空。
 */

// 注册 iX Web Components 与图标（子应用在无界 iframe 中拥有独立 window，需各自注册）
defineCustomElements();
defineIxIcons();

let app: VueApp | null = null;
let router: Router | null = null;

/** 创建并挂载 Vue 应用（Pinia 子应用独立注册，生命周期跟随本 iframe） */
function mount(): void {
  router = createRouter({ history: createWebHistory('/'), routes });
  app = createApp(App);
  app.use(createPinia());
  app.use(router);
  app.use(i18n);
  app.mount('#app');
}

/** 卸载 Vue 应用（单例模式切换时调用；保活模式下不调用） */
function unmount(): void {
  app?.unmount();
  app = null;
  router = null;
}

if (window.__POWERED_BY_WUJIE__) {
  // 运行在无界环境中：注册生命周期
  window.__WUJIE_MOUNT = mount;
  window.__WUJIE_UNMOUNT = unmount;
  // Vite 异步加载，主动触发一次挂载
  window.__WUJIE?.mount?.();
} else {
  // 独立运行（脱离无界，直接访问子应用地址时）
  mount();
}

// 初始化主题（独立运行也能切换；无界环境下监听主应用 bus 广播）
initTheme();
// 初始化语言（独立运行读 localStorage；无界环境下监听主应用 bus 广播）
initLocale();
