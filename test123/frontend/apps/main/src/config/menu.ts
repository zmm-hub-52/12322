/**
 * 菜单与子应用注册表
 *
 * 说明：
 * 1. appRegistry：逻辑子应用名 -> 入口 base url（生产环境替换为 nginx/CDN 地址）。
 * 2. menus：菜单项。同一子应用可配置多个菜单项（不同内部路由），
 *    每个菜单项打开为一个独立 Tab（唯一无界实例名），互不干扰。
 */

/** 子应用注册表 */
export const appRegistry: Record<string, { baseUrl: string; title: string }> = {
  approval: { baseUrl: 'http://localhost:5174', title: '审批中心' },
};

/** 菜单项 */
export interface MenuItem {
  /** 菜单标题（也作为 Tab 标题） */
  title: string;
  /** 逻辑子应用名（对应 appRegistry 的 key） */
  appName: string;
  /** 子应用内部路由路径 */
  route: string;
}

/** 菜单配置（真实场景由后端菜单接口下发 + 权限过滤） */
export const menus: MenuItem[] = [
  { title: '审批列表', appName: 'approval', route: '/approval/list' },
  { title: '审批详情', appName: 'approval', route: '/approval/detail/SP-2024-0001' },
];

/** 根据 appName + route 拼接子应用完整入口 url */
export function resolveSubAppUrl(appName: string, route: string): string {
  const reg = appRegistry[appName];
  if (!reg) throw new Error(`[menu] 未注册的子应用: ${appName}`);
  return `${reg.baseUrl}${route}`;
}
