import { startApp, destroyApp } from 'wujie';
import type {
  InstanceRecord,
  InstanceStatus,
  OpenTabInput,
  TabManagerOptions,
  TabMeta,
} from './types';

/**
 * TabManager：多 Tab + 无界子应用实例管理器（纯 TS，框架无关）。
 *
 * 核心职责：
 * 1. 维护打开的所有 Tab 元数据（含激活状态）
 * 2. 维护每个子应用实例（唯一 name）的运行记录与 10 分钟空闲定时器
 * 3. 实例销毁规则：
 *    - 后台（切走）后开始计时，超过 idleTimeout 未被重新激活 → 自动销毁
 *    - 关闭 tab → 销毁该 tab 自己的实例
 * 4. 将 Tab 元数据持久化到 sessionStorage，刷新后恢复
 *
 * 设计说明（与无界保活模式的关系）：
 * - 无界保活模式（alive: true）下，同一个 name 改 url 无法跳转不同路由，
 *   因此「一个子应用多个菜单项」必须各自使用唯一 name（一个 tab 一个实例）。
 * - appName 用于逻辑分组（统计某子应用还开着几个 tab），实例生命周期由 tab 自己管。
 */
export class TabManager {
  /** 所有打开的 tab（有序） */
  private tabs: TabMeta[] = [];
  /** 当前激活的 tab id */
  private activeTabId: string | null = null;
  /** 无界实例运行记录：key = 实例 name */
  private instances = new Map<string, InstanceRecord>();
  /** 状态变化监听器集合 */
  private listeners = new Set<() => void>();
  /** 空闲超时毫秒 */
  private readonly idleTimeout: number;
  /** sessionStorage 持久化 key */
  private readonly storageKey: string;
  /** props 工厂 */
  private readonly props?: TabManagerOptions['props'];
  /** 加载失败回调 */
  private readonly onLoadError?: TabManagerOptions['onLoadError'];
  /** 是否降级 iframe（默认 false，走无界默认隔离） */
  private readonly degrade: boolean;
  /** 自增序号，用于生成唯一 id */
  private seq = 0;

  constructor(options: TabManagerOptions = {}) {
    this.idleTimeout = options.idleTimeout ?? 10 * 60 * 1000; // 默认 10 分钟
    this.storageKey = options.storageKey ?? 'platform-tabs';
    this.props = options.props;
    this.onLoadError = options.onLoadError;
    this.degrade = options.degrade ?? false;
  }

  // ---------------------------------------------------------------------
  // 订阅
  // ---------------------------------------------------------------------

  /** 订阅状态变化（返回取消订阅函数），供 Vue 层绑定响应式 */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** 通知所有监听器状态已变化 */
  private notify(): void {
    this.listeners.forEach((fn) => fn());
  }

  // ---------------------------------------------------------------------
  // 只读访问
  // ---------------------------------------------------------------------

  /** 获取所有 tab（浅拷贝，避免外部直接改内部状态） */
  getTabs(): TabMeta[] {
    return [...this.tabs];
  }

  /** 获取当前激活 tab id */
  getActiveTabId(): string | null {
    return this.activeTabId;
  }

  /** 获取指定实例的状态 */
  getInstanceStatus(name: string): InstanceStatus | undefined {
    return this.instances.get(name)?.status;
  }

  /** 某逻辑子应用当前打开的 tab 数量（引用计数语义） */
  getAppTabCount(appName: string): number {
    return this.tabs.filter((t) => t.appName === appName).length;
  }

  /** 判断某 tab 是否需要挂载（无实例 / 已销毁 / 加载失败） */
  needsMount(tabId: string): boolean {
    const tab = this.findTab(tabId);
    if (!tab) return false;
    const status = this.instances.get(tab.name)?.status;
    return status !== 'loading' && status !== 'loaded';
  }

  // ---------------------------------------------------------------------
  // 核心操作
  // ---------------------------------------------------------------------

  /**
   * 打开一个 tab（只创建状态并激活，startApp 需在拿到 DOM 容器后调用 mountTab）。
   * @returns 新建的 tab 元数据
   */
  openTab(input: OpenTabInput): TabMeta {
    const id = this.genId();
    const tab: TabMeta = {
      id,
      appName: input.appName,
      name: `${input.appName}-${id}`, // 唯一实例名
      title: input.title,
      url: input.url,
      route: input.route,
      active: false,
    };
    this.tabs.push(tab);
    this.activateTab(id);
    return tab;
  }

  /**
   * 将子应用实例挂载到容器 el 上（调用 startApp）。
   * 幂等：已 loading/loaded 则直接跳过。
   */
  async mountTab(tabId: string, el: HTMLElement): Promise<void> {
    const tab = this.findTab(tabId);
    if (!tab) return;

    const record = this.getOrCreateRecord(tab.name);
    if (record.status === 'loading' || record.status === 'loaded') return;

    record.el = el;
    record.status = 'loading';
    this.notify();

    try {
      // 无界保活模式：alive=true，实例/路由不丢，切换即热插拔
      const destroy = await startApp({
        name: tab.name,
        url: tab.url,
        el,
        alive: true,
        sync: true, // 路由同步：子应用路由同步到主应用 url，刷新/前进后退不丢
        degrade: this.degrade, // 默认 false：走无界默认 iframe 隔离
        props: this.props?.(tab),
        loadError: (url, e) => this.onLoadError?.(tab, e),
      });

      // 保护：挂载期间可能已被关闭/销毁
      if (!this.instances.has(tab.name)) return;

      // 无界 startApp 返回的 destroy 类型较宽松（Function | void），此处收窄
      record.destroy = destroy as unknown as (() => void);
      record.status = 'loaded';
      record.lastActiveAt = Date.now();

      // 当前激活则不计时；在后台则开始空闲计时
      if (tab.id === this.activeTabId) {
        this.clearIdleTimer(record);
      } else {
        this.startIdleTimer(record);
      }
    } catch (e) {
      record.status = 'error';
      this.onLoadError?.(tab, e as Error);
    }
    this.notify();
  }

  /**
   * 激活某个 tab：切换激活状态 + 重置空闲定时器。
   * 注意：若实例已被超时销毁，真正的「重新加载」由 Vue 层在拿到 el 后调用 mountTab 完成。
   */
  activateTab(tabId: string): void {
    const tab = this.findTab(tabId);
    if (!tab) return;

    // 前一个激活 tab 转为后台，开始空闲计时
    if (this.activeTabId && this.activeTabId !== tabId) {
      const prev = this.findTab(this.activeTabId);
      const prevRec = prev ? this.instances.get(prev.name) : undefined;
      if (prevRec && prevRec.status === 'loaded') {
        this.startIdleTimer(prevRec);
      }
    }

    this.activeTabId = tabId;
    this.tabs.forEach((t) => (t.active = t.id === tabId));

    const record = this.instances.get(tab.name);
    if (record && record.status === 'loaded') {
      record.lastActiveAt = Date.now();
      this.clearIdleTimer(record); // 激活即停止后台计时
    }

    this.persist();
    this.notify();
  }

  /** 取消激活所有 tab（回到主应用首页时调用），后台实例进入空闲计时 */
  clearActive(): void {
    this.tabs.forEach((t) => {
      t.active = false;
      const record = this.instances.get(t.name);
      if (record && record.status === 'loaded') this.startIdleTimer(record);
    });
    this.activeTabId = null;
    this.persist();
    this.notify();
  }

  /** 关闭 tab：销毁其实例 + 移除 tab，并激活相邻 tab */
  closeTab(tabId: string): void {
    const idx = this.tabs.findIndex((t) => t.id === tabId);
    if (idx < 0) return;

    const tab = this.tabs[idx];
    const record = this.instances.get(tab.name);
    if (record) {
      this.clearIdleTimer(record);
      if (record.status === 'loading' || record.status === 'loaded') {
        destroyApp(tab.name); // 销毁 iframe + shadowRoot + 实例缓存
      }
      this.instances.delete(tab.name);
    }

    this.tabs.splice(idx, 1);

    // 关闭的是当前激活 tab 时，激活相邻 tab
    if (this.activeTabId === tabId) {
      const next = this.tabs[Math.min(idx, this.tabs.length - 1)];
      if (next) {
        this.activeTabId = null; // 先置空，避免 activateTab 误判 prev
        this.activateTab(next.id);
      } else {
        this.activeTabId = null;
      }
    }

    this.persist();
    this.notify();
  }

  // ---------------------------------------------------------------------
  // 持久化
  // ---------------------------------------------------------------------

  /** 将 tab 元数据（不含实例/DOM）写入 sessionStorage */
  persist(): void {
    const data = {
      activeTabId: this.activeTabId,
      tabs: this.tabs.map((t) => ({
        id: t.id,
        appName: t.appName,
        name: t.name,
        title: t.title,
        url: t.url,
        route: t.route,
      })),
    };
    sessionStorage.setItem(this.storageKey, JSON.stringify(data));
  }

  /**
   * 从 sessionStorage 恢复 tab 列表。
   * 只恢复元数据（tab 栏完整还原），实例由 Vue 层按需挂载（当前激活的立即挂，其余懒加载）。
   * @returns 恢复出的 tab 列表
   */
  restore(): TabMeta[] {
    try {
      const raw = sessionStorage.getItem(this.storageKey);
      if (!raw) return [];
      const data = JSON.parse(raw) as { activeTabId?: string | null; tabs?: Partial<TabMeta>[] };
      const tabs: TabMeta[] = (data.tabs ?? []).map((t) => ({
        id: t.id ?? '',
        appName: t.appName ?? '',
        name: t.name ?? '',
        title: t.title ?? '',
        url: t.url ?? '',
        route: t.route ?? '',
        active: false,
      }));
      this.tabs = tabs;
      this.activeTabId = data.activeTabId ?? tabs[0]?.id ?? null;
      this.tabs.forEach((t) => (t.active = t.id === this.activeTabId));
      return this.getTabs();
    } catch {
      return [];
    }
  }

  // ---------------------------------------------------------------------
  // 内部工具
  // ---------------------------------------------------------------------

  /** 生成唯一 tab id */
  private genId(): string {
    this.seq += 1;
    return `${Date.now().toString(36)}-${this.seq}`;
  }

  private findTab(id: string): TabMeta | undefined {
    return this.tabs.find((t) => t.id === id);
  }

  private getOrCreateRecord(name: string): InstanceRecord {
    let record = this.instances.get(name);
    if (!record) {
      record = {
        name,
        // 初始态必须是 idle：若初始为 loading，会被 mountTab 的幂等守卫直接拦掉，
        // 导致 startApp 永远不被调用（子应用永远挂载不上）。
        status: 'idle',
        el: null,
        destroy: null,
        idleTimer: null,
        lastActiveAt: 0,
      };
      this.instances.set(name, record);
    }
    return record;
  }

  /** 启动空闲定时器：超时后销毁实例（tab 保留，下次激活重新加载） */
  private startIdleTimer(record: InstanceRecord): void {
    this.clearIdleTimer(record);
    record.idleTimer = window.setTimeout(() => {
      record.destroy?.();
      destroyApp(record.name);
      record.status = 'destroyed';
      record.destroy = null;
      record.idleTimer = null;
      this.notify();
    }, this.idleTimeout);
  }

  /** 清除空闲定时器 */
  private clearIdleTimer(record: InstanceRecord): void {
    if (record.idleTimer != null) {
      window.clearTimeout(record.idleTimer);
      record.idleTimer = null;
    }
  }
}
