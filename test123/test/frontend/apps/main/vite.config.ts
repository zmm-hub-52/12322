import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 主应用 Vite 配置
 * 端口 5173，子应用 5174。
 */
export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          // iX 的 <ix-*> 是 Web Components，告诉 Vue 编译器不要把它们当普通组件解析
          isCustomElement: (tag) => tag.startsWith('ix-'),
        },
      },
    }),
  ],
  // 每次启动强制重新预构建依赖：彻底避免旧缓存导致的组件渲染异常
  optimizeDeps: {
    force: true,
  },
  server: {
    port: 5173,
  },
});
