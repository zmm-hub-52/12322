<template>
  <div class="layout">
    <!-- 侧边菜单 -->
    <aside class="layout__sidebar">
      <div class="layout__brand">微服务生态底座</div>
      <nav class="menu">
        <a v-for="item in menus" :key="item.title" class="menu__item" @click="onMenuClick(item)">
          {{ item.title }}
        </a>
      </nav>
    </aside>

    <!-- 主区域 -->
    <div class="layout__main">
      <!-- 顶栏 -->
      <header class="topbar">
        <a class="topbar__home" @click="onHomeClick">首页</a>
        <div class="topbar__right">
          <button class="btn" @click="themeStore.toggle()">
            {{ themeStore.mode === 'light' ? '深色' : '浅色' }}
          </button>
          <span class="topbar__user">{{ userStore.userInfo?.name ?? '未登录' }}</span>
          <button class="btn" @click="onLogout">退出</button>
        </div>
      </header>

      <!-- Tab 栏 -->
      <Tabbar
        :tabs="tabs.state.tabs"
        :active-tab-id="tabs.state.activeTabId"
        @activate="tabs.activateTab"
        @close="tabs.closeTab"
      />

      <!-- 内容区：首页 + 各子应用容器 -->
      <main class="layout__content">
        <!-- 首页（无激活 tab 时展示） -->
        <div v-show="tabs.state.activeTabId === null" class="content-pane">
          <router-view />
        </div>
        <!-- 各子应用容器（v-show 切换，保活实例驻留在各自容器中） -->
        <div
          v-for="tab in tabs.state.tabs"
          :key="tab.id"
          v-show="tab.active"
          class="content-pane content-pane--app"
          :ref="(el) => tabs.setContainer(tab.id, el)"
        ></div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 默认布局：侧边菜单 + 顶栏 + 多 Tab 栏 + 内容区。
 * 内容区为每个打开的 tab 渲染一个容器 div（v-show 切换显隐），
 * 无界子应用实例挂载进各自的容器，保活模式下切走不销毁。
 */
import { useRouter } from 'vue-router';
import { menus, resolveSubAppUrl, type MenuItem } from '../config/menu';
import { useTabs } from '../composables/useTabs';
import { useUserStore } from '../stores/user';
import { useThemeStore } from '../stores/theme';
import Tabbar from '../components/Tabbar.vue';

const router = useRouter();
const userStore = useUserStore();
const themeStore = useThemeStore();
const tabs = useTabs(router);

// 初始化：恢复 sessionStorage 的 tab 列表（激活的立即挂载，其余懒加载）
tabs.init();

/** 点击菜单：打开对应子应用 tab */
function onMenuClick(item: MenuItem): void {
  tabs.openTab({
    appName: item.appName,
    title: item.title,
    url: resolveSubAppUrl(item.appName, item.route),
    route: item.route,
  });
}

/** 点击首页：取消激活所有 tab，回到欢迎页 */
function onHomeClick(): void {
  tabs.clearActive();
  router.push('/');
}

/** 退出登录 */
function onLogout(): void {
  userStore.clear();
  router.push('/login');
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  background: var(--brand-bg);
  color: var(--brand-text);
}
.layout__sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--brand-bg-secondary);
  border-right: 1px solid var(--brand-border);
  display: flex;
  flex-direction: column;
}
.layout__brand {
  height: 48px;
  line-height: 48px;
  padding: 0 16px;
  font-weight: 700;
  color: var(--brand-primary);
  border-bottom: 1px solid var(--brand-border);
}
.menu {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.menu__item {
  padding: 8px 12px;
  border-radius: var(--brand-radius);
  color: var(--brand-text);
  cursor: pointer;
}
.menu__item:hover {
  background: var(--brand-bg-tertiary);
  color: var(--brand-primary);
}
.layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--brand-border);
}
.topbar__home {
  cursor: pointer;
  color: var(--brand-primary);
  font-weight: 600;
}
.topbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.topbar__user {
  font-size: 13px;
  color: var(--brand-text-secondary);
}
.btn {
  padding: 4px 10px;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius);
  background: var(--brand-bg-secondary);
  color: var(--brand-text);
  cursor: pointer;
}
.layout__content {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--brand-bg);
}
.content-pane {
  height: 100%;
  overflow: auto;
  padding: 0 16px 16px;
}
.content-pane--app {
  padding: 0;
  overflow: hidden;
}
</style>
