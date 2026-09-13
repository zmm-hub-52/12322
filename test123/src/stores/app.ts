import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用级全局状态：标题、主题等。
 */
export const useAppStore = defineStore('app', () => {
  const title = ref(import.meta.env.VITE_APP_TITLE || '报修系统')

  /** iX 主题模式：light | dark（后续可接入 ix 主题切换） */
  const theme = ref<'light' | 'dark'>('light')

  function setTheme(value: 'light' | 'dark') {
    theme.value = value
  }

  function setTitle(value: string) {
    title.value = value
    document.title = value
  }

  return { title, theme, setTheme, setTitle }
})
