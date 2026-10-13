import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 主应用 Vite 配置
 * 端口 5173，子应用 5174。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
  },
});
