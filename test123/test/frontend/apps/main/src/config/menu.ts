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
  workflowAdmin: { baseUrl: 'http://localhost:5176', title: '流程配置中心' },
};

/** 菜单项 */
export interface MenuItem {
  /** 菜单标题的 i18n key（渲染时用 $t 翻译，也作为 Tab 标题） */
  titleKey: string;
  /** 逻辑子应用名（对应 appRegistry 的 key） */
  appName: string;
  /** 子应用内部路由路径 */
  route: string;
}

/** 菜单配置（真实场景由后端菜单接口下发 + 权限过滤） */
export const menus: MenuItem[] = [
  { titleKey: 'menu.approvalStart', appName: 'approval', route: '/approval/start' },
  { titleKey: 'menu.approvalList', appName: 'approval', route: '/approval/list' },
  { titleKey: 'menu.processConfig', appName: 'workflowAdmin', route: '/workflow-admin/process' },
  { titleKey: 'menu.formConfig', appName: 'workflowAdmin', route: '/workflow-admin/form' },
  { titleKey: 'menu.dimensionConfig', appName: 'workflowAdmin', route: '/workflow-admin/dimension' },
];

/** 根据 appName + route 拼接子应用完整入口 url */
export function resolveSubAppUrl(appName: string, route: string): string {
  const reg = appRegistry[appName];
  if (!reg) throw new Error(`[menu] 未注册的子应用: ${appName}`);
  return `${reg.baseUrl}${route}`;
}
