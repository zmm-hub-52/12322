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
  server: {
    port: 5173,
  },
});
