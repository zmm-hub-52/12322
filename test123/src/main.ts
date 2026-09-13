import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// Siemens iX：引入主题样式 + 注册全部 Web Components
import { defineCustomElements } from '@siemens/ix/loader'
import '@siemens/ix/dist/siemens-ix/siemens-ix.css'

import './styles/main.css'

// 设置 iX 主题（classic 主题 + light 配色；切暗色改为 dark）
document.documentElement.setAttribute('data-ix-theme', 'classic')
document.documentElement.setAttribute('data-ix-color-schema', 'light')

// 注册所有 ix-* 自定义元素（用于布局/原生标签）；
// 表单等交互组件推荐使用 @siemens/ix-vue 的 PascalCase 组件（IxInput 等，支持 v-model）
defineCustomElements()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
