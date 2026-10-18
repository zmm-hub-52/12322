import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 子应用模板：端口顺延（5175 起），必须开启 CORS 供主应用 fetch 入口
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    cors: true,
    headers: { 'Access-Control-Allow-Origin': '*' },
  },
});
