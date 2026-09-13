import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue({
        template: {
          compilerOptions: {
            // 把 ix-* 前缀当作原生自定义元素处理，避免被 Vue 编译器误解析。
            // 使用 @siemens/ix-vue 时同样安全：它注册的是 PascalCase 组件（IxButton 等）。
            isCustomElement: (tag) => tag.startsWith('ix-'),
          },
        },
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: true,
      port: 5173,
      proxy: {
        // 后端接口代理（对接 Flowable 后端时启用/修改）
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
