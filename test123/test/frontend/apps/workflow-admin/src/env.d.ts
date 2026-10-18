/// <reference types="vite/client" />

// 让 TS 识别 .vue 模块
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

// 无界注入的全局变量类型声明
declare global {
  interface Window {
    /** 子应用是否运行在无界环境中 */
    __POWERED_BY_WUJIE__?: boolean;
    /** 子应用 mount 生命周期（单例模式切换时调用） */
    __WUJIE_MOUNT?: () => void;
    /** 子应用 unmount 生命周期 */
    __WUJIE_UNMOUNT?: () => void;
    /** 无界沙盒实例（Vite 异步加载时手动调用 mount） */
    __WUJIE?: { mount: () => void };
    /** 无界注入对象：bus 通信 / props / 正确 location */
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
