/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

// 无界注入的全局变量类型声明
declare global {
  interface Window {
    __POWERED_BY_WUJIE__?: boolean;
    __WUJIE_MOUNT?: () => void;
    __WUJIE_UNMOUNT?: () => void;
    __WUJIE?: { mount: () => void };
    $wujie?: {
      bus?: {
        $on: (event: string, fn: (...args: unknown[]) => void) => void;
        $off: (event: string, fn: (...args: unknown[]) => void) => void;
        $emit: (event: string, ...args: unknown[]) => void;
      };
      props?: Record<string, unknown>;
      location?: { host: string; hostname: string; protocol: string };
    };
  }
}

export {};
