import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { initPlatformEvents } from './wujie';
import { useThemeStore } from './stores/theme';
import { useLocaleStore } from './stores/locale';
import { i18n } from './i18n';
import { defineCustomElements } from '@siemens/ix/loader';
import { defineCustomElements as defineIxIcons } from '@siemens/ix-icons/loader';
import '@platform/ui-theme/theme.css';

/**
 * 主应用入口
 * 1. 装配 pinia + router
 * 2. 初始化主题（恢复 localStorage 偏好）
 * 3. 初始化跨应用事件（监听子应用上报的 token 过期等事件）
 */
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(i18n);

// 初始化主题与语言（pinia 已激活，可在此时取 store）
useThemeStore().init();
useLocaleStore().init();

// 初始化跨应用事件监听（实时数据经 props 传函数下发，见 useTabs）
initPlatformEvents();

// 注册 iX Web Components 与图标（<ix-*> 标签 + <ix-icon>）
defineCustomElements();
defineIxIcons();

app.mount('#app');
