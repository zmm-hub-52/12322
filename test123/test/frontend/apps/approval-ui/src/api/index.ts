/**
 * approval-ui 使用端 API：对接 workflow-service（http://localhost:8090）。
 * createSubAppHttp：token 经主应用 props 实时获取、401 经 bus 通知主应用。
 */

import { createSubAppHttp } from '@platform/sdk';
import type {
  GraphView,
  ImportPreview,
  NodeSchema,
  PublishedDef,
  RecordView,
  TaskView,
  UserView,
} from '../types/schema';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090';

const http = createSubAppHttp(API_BASE_URL);

interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

async function unwrap<T>(p: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await p;
  const body = res.data;
  if (body.code !== 0) {
    throw new Error(body.message || '接口调用失败');
  }
  return body.data;
}

// ==================== 流程 ====================

/** 已发布流程列表（发起页） */
export async function fetchPublishedDefs(): Promise<PublishedDef[]> {
  const list = await unwrap<
    { id: number; code: string; name: string; version: number; status: string; description: string | null }[]
  >(http.get('/api/process'));
  return (list ?? [])
    .filter((d) => d.status === 'published')
    .map((d) => ({ id: d.id, code: d.code, name: d.name, version: d.version, description: d.description }));
}

/** 流程最新版本快照（发起表单预览） */
export async function fetchDefSchema(defId: number): Promise<{ schemaVersion: string; nodes: Record<string, unknown> }> {
  return unwrap<{ schemaVersion: string; nodes: Record<string, unknown> }>(http.get(`/api/process/${defId}/schema`));
}

/** 流程整图（找审批节点选处理人） */
export async function fetchGraph(defId: number): Promise<GraphView> {
  return unwrap<GraphView>(http.get(`/api/process/${defId}/graph`));
}

// ==================== 用户 ====================

/** 用户列表（选审批人） */
export async function fetchUsers(): Promise<UserView[]> {
  return unwrap<UserView[]>(http.get('/api/auth/users'));
}

// ==================== 实例 / 任务 ====================

/** 发起流程 */
export async function startInstance(payload: {
  processDefId: number;
  applicant: string;
  bizNo?: string;
  title?: string;
  formData: Record<string, unknown>;
  assignees: Record<string, string>;
}): Promise<{ id: number }> {
  return unwrap<{ id: number }>(http.post('/api/instance/start', payload));
}

/** 我的待办 */
export async function fetchMyTasks(assignee: string): Promise<TaskView[]> {
  return unwrap<TaskView[]>(http.get('/api/task/my', { params: { assignee } }));
}

/** 实例当前节点 schema */
export async function fetchNodeSchema(instanceId: number): Promise<NodeSchema> {
  return unwrap<NodeSchema>(http.get(`/api/instance/${instanceId}/schema`));
}

/** 填写节点提交 */
export async function submitTask(taskId: string, formData: Record<string, unknown>): Promise<void> {
  await unwrap<unknown>(http.post('/api/task/submit', { taskId, formData }));
}

/** 审批（通过/驳回） */
export async function approveTask(taskId: string, approved: boolean, comment?: string): Promise<void> {
  await unwrap<unknown>(http.post('/api/task/approve', { taskId, approved, comment }));
}

/** 实例轨迹 */
export async function fetchRecords(instanceId: number): Promise<RecordView[]> {
  return unwrap<RecordView[]>(http.get(`/api/instance/${instanceId}/records`));
}

// ==================== Excel 导入发起 ====================

/** Excel 模板下载地址（直接浏览器下载） */
export function excelTemplateUrl(defId: number): string {
  return `${API_BASE_URL}/api/process/${defId}/excel-template`;
}

/** 上传 Excel 解析校验（预览） */
export async function previewExcel(defId: number, file: File): Promise<ImportPreview> {
  const form = new FormData();
  form.append('file', file);
  form.append('defId', String(defId));
  const res = await http.post('/api/instance/excel/preview', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  const body = res.data as ApiResult<ImportPreview>;
  if (body.code !== 0) {
    throw new Error(body.message || '解析失败');
  }
  return body.data;
}

/** 预览通过的行逐条发起 */
export async function startExcelRow(payload: {
  processDefId: number;
  data: Record<string, string>;
  applicant: string;
  assignees: Record<string, string>;
  bizNo: string;
  title: string;
}): Promise<{ id: number }> {
  return unwrap<{ id: number }>(http.post('/api/instance/excel/start', payload));
}
