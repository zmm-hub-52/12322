import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// Siemens iX：引入主题样式 + 注册全部 Web Components
import { defineCustomElements } from '@siemens/ix/loader'
import '@siemens/ix/dist/siemens-ix/siemens-ix.css'

// Siemens iX Icons：注册 ix-icon 图标组件（登录页主题切换、箭头等使用）
import { defineCustomElements as defineIxIcons } from '@siemens/ix-icons/loader'

import './styles/main.css'

// 设置 iX 主题（classic 主题 + light/dark 配色；配色可持久化，见 stores/app.ts）
document.documentElement.setAttribute('data-ix-theme', 'classic')
document.documentElement.setAttribute(
  'data-ix-color-schema',
  localStorage.getItem('ix-color-schema') === 'dark' ? 'dark' : 'light',
)

// 注册所有 ix-* 自定义元素（布局/原生标签）与 ix-icon 图标；
// 表单等交互组件推荐使用 @siemens/ix-vue 的 PascalCase 组件（IxInput 等，支持 v-model）
defineCustomElements()
defineIxIcons()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
