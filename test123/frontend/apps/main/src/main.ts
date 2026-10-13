import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { initPlatformBridge } from './wujie';
import { useThemeStore } from './stores/theme';
import '@platform/ui-theme/theme.css';

/**
 * 主应用入口
 * 1. 装配 pinia + router
 * 2. 初始化主题（恢复 localStorage 偏好）
 * 3. 暴露跨应用桥 window.__PLATFORM__（token/用户/主题，供子应用实时读取）
 */
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

// 初始化主题（pinia 已激活，可在此时取 store）
useThemeStore().init();

// 暴露跨应用桥 + 监听子应用 token 过期事件
initPlatformBridge();

app.mount('#app');
