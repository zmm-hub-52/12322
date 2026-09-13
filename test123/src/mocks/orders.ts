/**
 * 报修单假数据（用于列表方案 A / B 对比演示）。
 * 业务阶段对接 Flowable 后端后，替换为真实接口数据。
 */

export type OrderStatus =
  | 'pending' // 待接单
  | 'in_progress' // 处理中
  | 'resolved' // 待确认
  | 'closed' // 已关闭
  | 'cancelled' // 已取消
  | 'rejected' // 已驳回

export type OrderPriority = 'high' | 'medium' | 'low'

export interface RepairOrder {
  id: string
  title: string
  category: string
  reporter: string
  priority: OrderPriority
  status: OrderStatus
  createdAt: string
  assignee?: string
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  pending: '待接单',
  in_progress: '处理中',
  resolved: '待确认',
  closed: '已关闭',
  cancelled: '已取消',
  rejected: '已驳回',
}

/** 状态 → ix-pill/ix-chip 的 variant */
export const ORDER_STATUS_VARIANT: Record<OrderStatus, string> = {
  pending: 'info',
  in_progress: 'warning',
  resolved: 'primary',
  closed: 'success',
  cancelled: 'neutral',
  rejected: 'critical',
}

export const ORDER_PRIORITY_LABEL: Record<OrderPriority, string> = {
  high: '高',
  medium: '中',
  low: '低',
}

export const ORDER_PRIORITY_COLOR: Record<OrderPriority, string> = {
  high: '#d72332',
  medium: '#947100',
  low: '#2c8500',
}

export const mockOrders: RepairOrder[] = [
  { id: 'BX2026091201', title: '三楼打印机卡纸无法打印', category: '设备故障', reporter: '王芳', priority: 'medium', status: 'pending', createdAt: '2026-09-12 09:15' },
  { id: 'BX2026091202', title: '办公区无线网络频繁掉线', category: '网络问题', reporter: '李强', priority: 'high', status: 'in_progress', createdAt: '2026-09-12 08:40', assignee: '张伟' },
  { id: 'BX2026091103', title: 'ERP 系统登录报 500 错误', category: '软件异常', reporter: '赵敏', priority: 'high', status: 'in_progress', createdAt: '2026-09-11 17:22', assignee: '刘洋' },
  { id: 'BX2026091104', title: '会议室投影仪画面偏色', category: '设备故障', reporter: '孙悦', priority: 'low', status: 'resolved', createdAt: '2026-09-11 16:05', assignee: '张伟' },
  { id: 'BX2026091105', title: '门禁卡无法刷开东侧门', category: '其他', reporter: '周杰', priority: 'medium', status: 'closed', createdAt: '2026-09-11 14:30', assignee: '刘洋' },
  { id: 'BX2026091006', title: '财务部电脑开机蓝屏', category: '设备故障', reporter: '吴丽', priority: 'high', status: 'closed', createdAt: '2026-09-10 11:20', assignee: '张伟' },
  { id: 'BX2026091007', title: '邮箱无法收发外部邮件', category: '网络问题', reporter: '郑浩', priority: 'medium', status: 'pending', createdAt: '2026-09-10 10:05' },
  { id: 'BX2026091008', title: '共享盘权限需要开通', category: '其他', reporter: '冯雪', priority: 'low', status: 'rejected', createdAt: '2026-09-10 09:00', assignee: '刘洋' },
  { id: 'BX2026090909', title: '车间 MES 终端触摸失灵', category: '设备故障', reporter: '陈龙', priority: 'high', status: 'resolved', createdAt: '2026-09-09 15:45', assignee: '张伟' },
  { id: 'BX2026090910', title: '打印服务器排队异常', category: '软件异常', reporter: '何静', priority: 'medium', status: 'closed', createdAt: '2026-09-09 13:30', assignee: '刘洋' },
  { id: 'BX2026090911', title: '会议室空调温度无法调节', category: '设备故障', reporter: '林涛', priority: 'low', status: 'cancelled', createdAt: '2026-09-09 11:10' },
  { id: 'BX2026090812', title: 'VPN 连接后无法访问内网系统', category: '网络问题', reporter: '许静', priority: 'high', status: 'closed', createdAt: '2026-09-08 16:50', assignee: '张伟' },
  { id: 'BX2026090813', title: '开发环境数据库连接超时', category: '软件异常', reporter: '罗文', priority: 'high', status: 'in_progress', createdAt: '2026-09-08 14:20', assignee: '刘洋' },
  { id: 'BX2026090814', title: '一楼大厅电子屏黑屏', category: '设备故障', reporter: '宋佳', priority: 'medium', status: 'pending', createdAt: '2026-09-08 10:30' },
  { id: 'BX2026090715', title: 'OA 审批流程无法提交', category: '软件异常', reporter: '唐磊', priority: 'medium', status: 'resolved', createdAt: '2026-09-07 17:00', assignee: '刘洋' },
  { id: 'BX2026090716', title: '新员工账号未开通', category: '其他', reporter: '韩雪', priority: 'low', status: 'closed', createdAt: '2026-09-07 15:25', assignee: '张伟' },
  { id: 'BX2026090717', title: '仓库扫码枪无法连接', category: '设备故障', reporter: '曹阳', priority: 'high', status: 'pending', createdAt: '2026-09-07 09:40' },
  { id: 'BX2026090618', title: '访客网络无法访问', category: '网络问题', reporter: '马超', priority: 'low', status: 'rejected', createdAt: '2026-09-06 16:15', assignee: '刘洋' },
]
