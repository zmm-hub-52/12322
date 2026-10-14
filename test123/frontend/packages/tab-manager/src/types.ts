/**
 * tab-manager 公共类型定义
 */

/** Tab 元数据（其中 id/appName/name/title/url/route 会持久化到 sessionStorage） */
export interface TabMeta {
  /** tab 唯一 id（稳定；持久化后刷新恢复不丢） */
  id: string;
  /** 逻辑子应用名（如 approval），标识该 tab 属于哪个子应用 */
  appName: string;
  /** 无界实例名（唯一，形如 `${appName}-${id}`），保活模式下必须每个 tab 独立 */
  name: string;
  /** 菜单标题，显示在 tab 上 */
  title: string;
  /** 子应用入口完整 URL（协议 + 域名 + 端口 + 内部路由），直接传给 startApp */
  url: string;
  /** 子应用内部路由路径（如 /approval/list），用于展示与恢复 */
  route: string;
  /** 是否当前激活 */
  active: boolean;
}

/**
 * 实例状态
 * - idle：尚未挂载（新建记录时的初始态）
 * - loading：正在挂载（startApp 进行中）
 * - loaded：已挂载并保活
 * - destroyed：已被超时销毁，tab 仍在，下次激活需重新加载
 * - error：加载失败
 */
export type InstanceStatus = 'idle' | 'loading' | 'loaded' | 'destroyed' | 'error';

/** 无界实例运行记录（仅内存，不持久化） */
export interface InstanceRecord {
  /** 无界实例名（= TabMeta.name） */
  name: string;
  /** 实例当前状态 */
  status: InstanceStatus;
  /** 渲染容器 DOM（startApp 的 el；超时销毁后重载时复用） */
  el: HTMLElement | null;
  /** startApp 返回的 destroy 函数 */
  destroy: (() => void) | null;
  /** 10 分钟空闲定时器 id（null 表示未在计时） */
  idleTimer: number | null;
  /** 最近激活时间戳（毫秒） */
  lastActiveAt: number;
}

/** 打开 Tab 的入参 */
export interface OpenTabInput {
  /** 逻辑子应用名 */
  appName: string;
  /** 菜单标题 */
  title: string;
  /** 子应用入口完整 URL（base + route） */
  url: string;
  /** 子应用内部路由路径 */
  route: string;
}

/** 注入给子应用的 props 工厂：每次 startApp 时调用，保证拿到最新值 */
export type PropsFactory = (tab: TabMeta) => Record<string, unknown>;

/** TabManager 构造选项 */
export interface TabManagerOptions {
  /** 空闲超时毫秒，默认 10 分钟 */
  idleTimeout?: number;
  /** sessionStorage 持久化 key */
  storageKey?: string;
  /** 注入给子应用的 props 工厂 */
  props?: PropsFactory;
  /** 子应用加载失败回调（骨架屏/重试/兜底入口） */
  onLoadError?: (tab: TabMeta, error: Error) => void;
  /** 保活实例上限（默认 6）：存活实例数超过时，按「最早打开」淘汰非激活实例，防止内存峰值 */
  maxAlive?: number;
  /**
   * 是否采用降级 iframe 方案（degrade），默认 false。
   * false（默认）：无界默认 iframe 隔离，同域，window.parent 跨应用桥可用；
   * true：退回真实 iframe（src=子应用地址），兼容性更好但跨域，且弹窗无法覆盖整个应用。
   * 仅在遇到特定浏览器兼容问题时才需要开启。
   */
  degrade?: boolean;
}
