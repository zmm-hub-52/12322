import { createSubAppHttp } from '@platform/sdk';

/**
 * 审批模块 API。
 *
 * 说明：
 * 1. 网关地址走 VITE_API_BASE_URL（默认 http://localhost:8080/erp/api），
 *    后续换后端只需改环境变量，不改代码。
 * 2. createSubAppHttp 内部已处理：token 经 props 实时获取、401 经 bus 通知主应用。
 */

/** 网关地址（可用 VITE_API_BASE_URL 覆盖） */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/erp/api';

/** 审批模块 http 客户端 */
const http = createSubAppHttp(API_BASE_URL);

/** 审批状态 */
export type ApprovalStatus = 'pending' | 'approved' | 'rejected';

/** 审批记录 */
export interface ApprovalRecord {
  id: string;
  no: string;
  title: string;
  applicant: string;
  status: ApprovalStatus;
  time: string;
}

/** 审批列表查询参数 */
export interface ApprovalQuery {
  keyword?: string;
  status?: string;
}

/** 查询审批列表：GET /erp/api/approval/list */
export function fetchApprovalList(params: ApprovalQuery = {}): Promise<ApprovalRecord[]> {
  return http.get<ApprovalRecord[]>('/approval/list', { params }).then((res) => res.data);
}
