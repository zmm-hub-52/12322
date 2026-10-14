import { reactive, readonly, nextTick } from 'vue';
import { TabManager, type OpenTabInput, type TabMeta } from '@platform/tab-manager';
import type { Router } from 'vue-router';
import { useUserStore } from '../stores/user';
import { useThemeStore } from '../stores/theme';

/**
 * useTabs：把 TabManager（纯 TS）绑定到 Vue 响应式，并负责容器 DOM 的挂载调度。
 *
 * 关键协作：
 * - TabManager 只管状态 + 无界 startApp/destroyApp + 定时器 + 持久化；
 * - 本 composable 负责：把状态同步为 Vue 响应式、把容器 DOM 传给 TabManager 挂载。
 */
export function useTabs(router: Router) {
  const userStore = useUserStore();
  const themeStore = useThemeStore();

  const tabManager = new TabManager({
    // 空闲超时（毫秒）：默认 10 分钟（保活实例超过该时长未被激活则自动销毁）。
    // 可用 VITE_TAB_IDLE_TIMEOUT 覆盖，便于联调与自动化验证。
    idleTimeout: Number(import.meta.env.VITE_TAB_IDLE_TIMEOUT) || 10 * 60 * 1000,
    // 注入给子应用的 props：
    // - 数据（user）是快照，静态展示用；
    // - 函数（getToken/getUser/getTheme/jump）是活引用，子应用每次调用拿最新值。
    // 默认 iframe 同域下函数可跨应用传递（降级模式跨域则不可用，但本项目走默认模式）。
    props: (tab) => ({
      user: userStore.userInfo,
      getToken: () => userStore.token || null,
      getUser: () => userStore.userInfo,
      getTheme: () => themeStore.mode,
      // 跨应用跳转：子应用通过 window.$wujie.props.jump 跳主应用其他菜单
      jump: (path: string) => router.push(path),
    }),
    onLoadError: (tab, error) => {
      // 兜底入口：真实场景这里显示骨架屏/失败重试/兜底页，禁止白屏
      console.error(`[useTabs] 子应用加载失败「${tab.title}」:`, error);
    },
    // 不传 degrade，走无界默认 iframe 隔离（同域，window.parent.__PLATFORM__ 跨应用桥可用）。
    // 实测（无头 Chrome + CDP 验证）：Vite dev 下默认模式同样能正常加载并渲染子应用，
    // 因此无需为开发环境开启降级 iframe。如遇个别浏览器兼容问题，可在此显式传 degrade: true。
  });

  // 响应式状态（供模板使用）
  const state = reactive({
    tabs: [] as TabMeta[],
    activeTabId: null as string | null,
  });

  // 订阅 TabManager 状态变化 -> 同步到响应式
  const sync = () => {
    state.tabs = tabManager.getTabs();
    state.activeTabId = tabManager.getActiveTabId();
  };
  tabManager.subscribe(sync);

  // 容器 DOM 映射：tab.id -> HTMLElement
  const containers = new Map<string, HTMLElement>();

  /** 容器 ref 回调：DOM 创建/销毁时触发 */
  function setContainer(tabId: string, el: unknown): void {
    if (el instanceof HTMLElement) {
      containers.set(tabId, el);
      // 新容器挂载后，若是激活 tab 且需要挂载，立即 startApp
      if (tabManager.getActiveTabId() === tabId && tabManager.needsMount(tabId)) {
        void tabManager.mountTab(tabId, el);
      }
    } else {
      containers.delete(tabId);
    }
  }

  /** 打开菜单 -> 新 tab（挂载由容器 ref 回调触发） */
  function openTab(input: OpenTabInput): void {
    tabManager.openTab(input);
  }

  /** 激活某个 tab */
  function activateTab(tabId: string): void {
    tabManager.activateTab(tabId);
    // 懒加载 / 超时销毁后重新加载：容器已在 DOM，nextTick 后挂载
    nextTick(() => {
      const el = containers.get(tabId);
      if (el && tabManager.needsMount(tabId)) {
        void tabManager.mountTab(tabId, el);
      }
    });
  }

  /** 关闭某个 tab */
  function closeTab(tabId: string): void {
    tabManager.closeTab(tabId);
  }

  /** 回到首页：取消激活所有 tab（后台实例进入空闲计时） */
  function clearActive(): void {
    tabManager.clearActive();
  }

  /** 初始化：恢复 sessionStorage 的 tab 列表（只恢复元数据，实例按需挂载） */
  function init(): void {
    tabManager.restore();
    sync();
  }

  return {
    state: readonly(state),
    setContainer,
    openTab,
    activateTab,
    closeTab,
    clearActive,
    init,
  };
}
