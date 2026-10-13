/// <reference types="vite/client" />

// 让 TS 识别 .vue 模块
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

import type { PlatformUser } from '@platform/sdk';

// 主应用暴露给子应用的跨应用桥（子应用经 window.parent.__PLATFORM__ 读取）
declare global {
  interface Window {
    __PLATFORM__?: {
      getToken: () => string | null;
      getUser: () => PlatformUser | null;
      getTheme: () => 'light' | 'dark';
    };
  }
}

export {};
