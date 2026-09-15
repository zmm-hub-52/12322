import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用级全局状态：标题、主题等。
 */

const THEME_KEY = 'ix-color-schema'

function readTheme(): 'light' | 'dark' {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
}

export const useAppStore = defineStore('app', () => {
  const title = ref(import.meta.env.VITE_APP_TITLE || '报修系统')

  /** iX 配色模式：light | dark */
  const theme = ref<'light' | 'dark'>(readTheme())

  function applyTheme() {
    document.documentElement.setAttribute('data-ix-color-schema', theme.value)
    localStorage.setItem(THEME_KEY, theme.value)
  }

  function setTheme(value: 'light' | 'dark') {
    theme.value = value
    applyTheme()
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    applyTheme()
  }

  function setTitle(value: string) {
    title.value = value
    document.title = value
  }

  return { title, theme, setTheme, toggleTheme, setTitle }
})
