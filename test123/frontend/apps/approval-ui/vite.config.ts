import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 子应用 Vite 配置
 * 说明：
 * 1. 作为无界子应用被主应用加载，主应用通过 fetch 拉取本子应用的 index.html 与资源，
 *    因此开发服务器必须开启 CORS。
 * 2. 端口 5174（主应用 5173），避免冲突。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
  },
});
