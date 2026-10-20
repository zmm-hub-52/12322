/**
 * workflow-admin API 层：对接 workflow-service（http://localhost:8090）。
 *
 * 说明：
 * 1. createSubAppHttp：token 经主应用 props 实时获取、401 经 bus 通知主应用（SDK 统一处理）；
 * 2. 后端统一返回 Result{code, message, data}，此处解包：code!==0 抛错、code===0 返回 data；
 * 3. 后端实体字段与前端画布类型有差异（code/defCode、status/published、JSON 字符串/对象），
 *    本层做适配映射，页面/组件零改动。
 */

import type { AxiosResponse } from 'axios';
import { createSubAppHttp } from '@platform/sdk';
import type {
  FieldDef,
  NodeFormField,
  ProcessDef,
  ProcessLink,
  ProcessNode,
} from '../types/schema';

/** 后端服务地址（开发期直连；生产走网关） */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090';

const http = createSubAppHttp(API_BASE_URL);

/** 后端统一响应包装 */
interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/** 解包 Result：code!==0 抛错 */
async function unwrap<T>(p: Promise<AxiosResponse<ApiResult<T>>>): Promise<T> {
  const res = await p;
  const body = res.data;
  if (body.code !== 0) {
    throw new Error(body.message || '接口调用失败');
  }
  return body.data;
}

// ==================== 后端实体类型（与 Java 实体对齐） ====================

interface ApiProcessDef {
  id: number;
  code: string;
  name: string;
  version: number;
  status: string; // draft / published / disabled
  description: string | null;
  deploymentId: string | null;
  updateTime: string | null;
  publishedTime: string | null;
  enabled: boolean;
}

interface ApiProcessNode {
  id: number;
  processDefId: number;
  nodeKey: string;
  nodeName: string;
  nodeType: string;
  rejectStrategy: string | null;
  assigneeMode: string | null;
  fixedAssignee: string | null;
  enabled: boolean;
}

interface ApiLinkDto {
  id: number;
  fromKey: string;
  toKey: string;
  condition: {
    fieldKey?: string;
    fieldName?: string;
    operator?: string;
    value?: string;
    isDefault?: boolean;
  } | null;
  sortNo: number;
}

interface ApiFieldDef {
  id: number;
  categoryId: number | null;
  fieldKey: string;
  fieldName: string;
  description: string | null;
  fieldType: string;
  fieldRule: string | null;
  required: boolean;
  sortNo: number;
  enabled: boolean;
}

interface ApiNodeFormField {
  id: number;
  nodeId: number;
  fieldKey: string;
  orderNo: number;
  mode: string;
  override: string | null;
  enabled: boolean;
}

interface ApiDimension {
  id: number;
  dimensionCode: string;
  dimensionName: string;
  enabled: boolean;
}

interface ApiDimensionCategory {
  id: number;
  dimensionId: number;
  categoryCode: string;
  categoryName: string;
  sortNo: number;
  enabled: boolean;
}

// ==================== 适配函数 ====================

function parseJson<T>(json: string | null): T | undefined {
  if (!json) return undefined;
  try {
    return JSON.parse(json) as T;
  } catch {
    return undefined;
  }
}

/** 后端流程定义 → 前端 ProcessDef（不含 nodes/links，由 graph 接口补） */
function adaptProcessDef(a: ApiProcessDef): ProcessDef {
  return {
    id: a.id,
    defCode: a.code,
    defName: a.name,
    description: a.description ?? '',
    initiator: 'buyer',
    version: a.version,
    published: a.status === 'published',
    updateTime: a.updateTime ?? undefined,
    publishedTime: a.publishedTime ?? undefined,
    nodes: [],
    links: [],
  };
}

/** 后端节点 → 前端 ProcessNode */
function adaptNode(a: ApiProcessNode): ProcessNode {
  return {
    id: a.id,
    nodeKey: a.nodeKey,
    nodeName: a.nodeName,
    nodeType: a.nodeType as ProcessNode['nodeType'],
    rejectStrategy: (a.rejectStrategy ?? 'back_prev') as ProcessNode['rejectStrategy'],
    assigneeMode: (a.assigneeMode ?? 'dynamic') as ProcessNode['assigneeMode'],
    fixedAssignee: a.fixedAssignee ?? undefined,
  };
}

/** 后端连线 → 前端 ProcessLink */
function adaptLink(a: ApiLinkDto): ProcessLink {
  return {
    id: a.id,
    fromKey: a.fromKey,
    toKey: a.toKey,
    condition: a.condition
      ? {
          fieldKey: a.condition.fieldKey ?? '',
          fieldName: a.condition.fieldName ?? '',
          operator: a.condition.operator ?? '',
          value: a.condition.value ?? '',
          isDefault: a.condition.isDefault,
        }
      : undefined,
  };
}

/** 后端字段 → 前端 FieldDef（rule 为取值+校验二合一） */
function adaptFieldDef(a: ApiFieldDef): FieldDef {
  return {
    id: a.id,
    categoryId: a.categoryId ?? undefined,
    fieldKey: a.fieldKey,
    fieldName: a.fieldName,
    description: a.description ?? undefined,
    fieldType: a.fieldType as FieldDef['fieldType'],
    required: a.required,
    rule: parseJson(a.fieldRule),
    enabled: a.enabled,
  };
}

// ==================== 图 ====================

/** 后端图返回（nodes + links） */
interface ApiGraph {
  nodes: ApiProcessNode[];
  links: ApiLinkDto[];
}

/** 读整图 */
export async function apiGetGraph(defId: number): Promise<{ nodes: ProcessNode[]; links: ProcessLink[] }> {
  const g = await unwrap<ApiGraph>(http.get(`/api/process/${defId}/graph`));
  return {
    nodes: (g.nodes ?? []).map(adaptNode),
    links: (g.links ?? []).map(adaptLink),
  };
}

/** 保存整图（节点按 nodeKey upsert，连线全量重建） */
export async function apiSaveGraph(
  defId: number,
  nodes: ProcessNode[],
  links: ProcessLink[],
): Promise<void> {
  await unwrap<unknown>(
    http.put(`/api/process/${defId}/graph`, {
      nodes: nodes.map((n) => ({
        nodeKey: n.nodeKey,
        nodeName: n.nodeName,
        nodeType: n.nodeType,
        rejectStrategy: n.rejectStrategy ?? 'back_prev',
        assigneeMode: n.assigneeMode ?? 'dynamic',
        fixedAssignee: n.fixedAssignee ?? null,
      })),
      links: links.map((l) => ({
        fromKey: l.fromKey,
        toKey: l.toKey,
        sortNo: l.sortNo ?? 0,
        condition: l.condition
          ? {
              fieldKey: l.condition.fieldKey,
              fieldName: l.condition.fieldName,
              operator: l.condition.operator,
              value: l.condition.value,
              isDefault: l.condition.isDefault,
            }
          : null,
      })),
    }),
  );
}

// ==================== 流程定义 ====================

/** 流程列表 */
export async function apiListDefs(): Promise<ProcessDef[]> {
  const list = await unwrap<ApiProcessDef[]>(http.get('/api/process'));
  return (list ?? []).map(adaptProcessDef);
}

/** 流程详情（基本信息 + 图） */
export async function apiGetDef(id: number): Promise<ProcessDef | undefined> {
  const def = await unwrap<ApiProcessDef>(http.get(`/api/process/${id}`));
  const def2 = adaptProcessDef(def);
  const g = await apiGetGraph(id);
  def2.nodes = g.nodes;
  def2.links = g.links;
  return def2;
}

/** 新建流程（草稿） */
export async function apiCreateDef(input: { code: string; name: string; description?: string }): Promise<number> {
  const def = await unwrap<ApiProcessDef>(
    http.post('/api/process', { code: input.code, name: input.name, description: input.description ?? null }),
  );
  return def.id;
}

/** 更新流程基本信息（code 不可改） */
export async function apiUpdateDef(id: number, input: { name: string; description?: string }): Promise<void> {
  await unwrap<unknown>(
    http.put(`/api/process/${id}`, { name: input.name, description: input.description ?? null }),
  );
}

/** 发布（版本+1 + 快照 + BPMN 编译部署） */
export async function apiPublish(id: number): Promise<number> {
  const def = await unwrap<ApiProcessDef>(http.post(`/api/process/${id}/publish`));
  return def.version;
}

// ==================== 用户 ====================

interface ApiUser {
  id: number;
  username: string;
  name: string;
  roleCode: string | null;
  roleName: string | null;
}

/** 用户列表（固定处理人选择） */
export async function apiListUsers(): Promise<{ username: string; name: string }[]> {
  const list = await unwrap<ApiUser[]>(http.get('/api/auth/users'));
  return (list ?? []).map((u) => ({ username: u.username, name: u.name }));
}

// ==================== 字段库 ====================

/** 某分类下的字段列表（维表第三层） */
export async function apiListFieldsByCategory(categoryId: number): Promise<FieldDef[]> {
  const list = await unwrap<ApiFieldDef[]>(http.get(`/api/dimension/category/${categoryId}/field`));
  return (list ?? []).map(adaptFieldDef);
}

/** 全字段缓存（避免节点字段 join / 条件编辑器反复遍历维度分类；字段变更时失效） */
let allFieldsCache: Promise<FieldDef[]> | null = null;

function clearAllFieldsCache(): void {
  allFieldsCache = null;
}

/** 全部字段（遍历所有维度分类汇总；节点字段 join / 条件编辑器字段下拉用） */
export function apiListAllFields(): Promise<FieldDef[]> {
  if (!allFieldsCache) {
    allFieldsCache = (async () => {
      const dims = await unwrap<ApiDimension[]>(http.get('/api/dimension'));
      const fields: FieldDef[] = [];
      for (const d of dims ?? []) {
        const cats = await unwrap<ApiDimensionCategory[]>(http.get(`/api/dimension/${d.id}/category`));
        for (const c of cats ?? []) {
          fields.push(...(await apiListFieldsByCategory(c.id)));
        }
      }
      return fields;
    })();
  }
  return allFieldsCache;
}

/** 分类下新建字段 */
export async function apiCreateField(
  categoryId: number,
  input: {
    fieldKey: string;
    fieldName: string;
    description?: string;
    fieldType: string;
    required: boolean;
    rule?: unknown;
  },
): Promise<void> {
  await unwrap<unknown>(
    http.post(`/api/dimension/category/${categoryId}/field`, {
      fieldKey: input.fieldKey,
      fieldName: input.fieldName,
      description: input.description ?? null,
      fieldType: input.fieldType,
      required: input.required,
      fieldRule: input.rule ? JSON.stringify(input.rule) : null,
    }),
  );
  clearAllFieldsCache();
}

/** 更新字段（fieldKey 不可改） */
export async function apiUpdateField(
  id: number,
  input: { fieldName: string; description?: string; fieldType: string; required: boolean; rule?: unknown },
): Promise<void> {
  await unwrap<unknown>(
    http.put(`/api/field/${id}`, {
      fieldName: input.fieldName,
      description: input.description ?? null,
      fieldType: input.fieldType,
      required: input.required,
      fieldRule: input.rule ? JSON.stringify(input.rule) : null,
    }),
  );
  clearAllFieldsCache();
}

/** 停用字段（逻辑删除） */
export async function apiDeleteField(id: number): Promise<void> {
  await unwrap<unknown>(http.delete(`/api/field/${id}`));
  clearAllFieldsCache();
}

// ==================== 节点字段 ====================

/** 展开节点字段（后端 node_form_field + 字段库 join，override 覆盖） */
export async function apiListNodeFields(defId: number, nodeKey: string): Promise<NodeFormField[]> {
  const g = await apiGetGraph(defId);
  const node = g.nodes.find((n) => n.nodeKey === nodeKey);
  if (!node) return [];

  const raws = await unwrap<ApiNodeFormField[]>(http.get(`/api/process/nodes/${node.id}/fields`));
  const library = await apiListAllFields();
  const libByKey = new Map(library.map((f) => [f.fieldKey, f]));

  return (raws ?? []).map((r) => {
    const def = libByKey.get(r.fieldKey);
    const override = parseJson<{ required?: boolean; rule?: unknown }>(r.override);
    return {
      id: r.id,
      fieldKey: r.fieldKey,
      fieldName: def?.fieldName ?? r.fieldKey,
      fieldType: def?.fieldType ?? 'input',
      mode: (r.mode ?? 'editable') as NodeFormField['mode'],
      required: override?.required ?? def?.required ?? false,
      rule: (override?.rule as NodeFormField['rule']) ?? def?.rule,
      placeholder: def?.placeholder,
      sortNo: r.orderNo ?? 0,
    };
  });
}

/** 保存节点字段（diff：新增/更新/删除） */
export async function apiSaveNodeFields(
  defId: number,
  nodeKey: string,
  fields: NodeFormField[],
): Promise<void> {
  const g = await apiGetGraph(defId);
  const node = g.nodes.find((n) => n.nodeKey === nodeKey);
  if (!node) return;

  const existing = await unwrap<ApiNodeFormField[]>(http.get(`/api/process/nodes/${node.id}/fields`));
  const existingByKey = new Map((existing ?? []).map((f) => [f.fieldKey, f]));

  for (const f of fields) {
    const override = JSON.stringify({
      required: f.required,
      rule: f.rule,
    });
    const old = existingByKey.get(f.fieldKey);
    if (old) {
      await unwrap<unknown>(
        http.put(`/api/process/fields/${old.id}`, {
          fieldKey: f.fieldKey,
          mode: f.mode,
          orderNo: f.sortNo ?? 0,
          override,
        }),
      );
      existingByKey.delete(f.fieldKey);
    } else {
      await unwrap<unknown>(
        http.post(`/api/process/nodes/${node.id}/fields`, {
          fieldKey: f.fieldKey,
          mode: f.mode,
          orderNo: f.sortNo ?? 0,
          override,
        }),
      );
    }
  }
  // 删除图中已移除的字段
  for (const left of existingByKey.values()) {
    await unwrap<unknown>(http.delete(`/api/process/fields/${left.id}`));
  }
}

// ==================== 维表 ====================

export async function apiListDimensions(): Promise<{ id: number; dimensionCode: string; dimensionName: string }[]> {
  const list = await unwrap<ApiDimension[]>(http.get('/api/dimension'));
  return (list ?? []).map((d) => ({ id: d.id, dimensionCode: d.dimensionCode, dimensionName: d.dimensionName }));
}

export async function apiListCategories(
  dimensionId: number,
): Promise<{ id: number; dimensionId: number; categoryCode: string; categoryName: string }[]> {
  const list = await unwrap<ApiDimensionCategory[]>(http.get(`/api/dimension/${dimensionId}/category`));
  return (list ?? []).map((c) => ({
    id: c.id,
    dimensionId: c.dimensionId,
    categoryCode: c.categoryCode,
    categoryName: c.categoryName,
  }));
}

export async function apiCreateDimension(code: string, name: string): Promise<void> {
  await unwrap<unknown>(http.post('/api/dimension', { dimensionCode: code, dimensionName: name }));
}

export async function apiCreateCategory(dimensionId: number, code: string, name: string): Promise<void> {
  await unwrap<unknown>(
    http.post('/api/dimension/category', { dimensionId, categoryCode: code, categoryName: name }),
  );
}
