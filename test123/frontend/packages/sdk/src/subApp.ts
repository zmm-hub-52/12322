import { createHttp } from './http';
import { BUS_EVENTS } from './contract';
import type { PlatformProps } from './types';

/**
 * 子应用侧专用工具：读取无界注入的 $wujie（props / bus）。
 */

/** 无界 bus 的最小类型声明（只声明用到的部分） */
export interface WujieBus {
  $on: (event: string, fn: (...args: unknown[]) => void) => void;
  $off: (event: string, fn: (...args: unknown[]) => void) => void;
  $emit: (event: string, ...args: unknown[]) => void;
}

/** 无界注入到子应用 window 的 $wujie 运行时对象 */
export interface WujieRuntime {
  bus?: WujieBus;
  props?: Partial<PlatformProps>;
}

/**
 * 获取无界注入的 $wujie 对象。
 * 子应用运行在无界中才有；独立运行（直接访问子应用地址）时为 undefined。
 */
export function getWujie(): WujieRuntime | undefined {
  return (window as unknown as { $wujie?: WujieRuntime }).$wujie;
}

/**
 * 创建子应用专用的 http 客户端。
 *
 * 约定：
 * 1. token 从主应用经 props 下传的 getToken 函数实时获取（函数引用，每次拿最新值，避免 token 过期失效）；
 * 2. 401 时经无界 bus 通知主应用，由主应用统一刷新 token 或跳登录。
 *
 * 子应用「禁止自存 token」，统一走本工厂。
 * @param baseURL 网关地址（如 '/erp/api'）
 */
export function createSubAppHttp(baseURL?: string) {
  return createHttp({
    baseURL,
    getToken: () => getWujie()?.props?.getToken?.() ?? null,
    onUnauthorized: () => getWujie()?.bus?.$emit(BUS_EVENTS.TOKEN_EXPIRED),
  });
}
