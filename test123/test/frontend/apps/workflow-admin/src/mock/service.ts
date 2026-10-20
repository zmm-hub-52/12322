/**
 * workflow-admin 服务层：对接后端接口（api 层）。
 *
 * 说明：
 * 1. 流程/图/维表（维度→分类→字段）/节点字段全部走后端 workflow-service（http://localhost:8090）；
 * 2. 流程模式模板为前端交互概念，保留本地数据（MOCK_TEMPLATES）；
 * 3. 新建流程（尚未保存拿不到后端 id，defId=0）时，节点字段配置暂存本地，
 *    保存草稿后自动同步到后端。
 */

import * as api from '../api';
import { MOCK_TEMPLATES } from './data';
import type {
  Dimension,
  DimensionCategory,
  FieldDef,
  NodeFormField,
  ProcessDef,
  ProcessTemplate,
} from '../types/schema';

/** draft 模式（defId=0）节点字段本地缓存：nodeKey -> fields */
const draftNodeFields = new Map<string, NodeFormField[]>();

// ==================== 流程 ====================

/** 流程列表 */
export async function fetchProcessList(): Promise<ProcessDef[]> {
  return api.apiListDefs();
}

/** 流程详情（基本信息 + 节点/连线图） */
export async function fetchProcessDetail(id: number): Promise<ProcessDef | undefined> {
  return api.apiGetDef(id);
}

/** 流程模式模板（本地：前端交互概念，不落库） */
export async function fetchTemplates(): Promise<ProcessTemplate[]> {
  return MOCK_TEMPLATES;
}

/** 用户列表（固定处理人选择） */
export async function fetchUsers(): Promise<{ username: string; name: string }[]> {
  return api.apiListUsers();
}

/** 保存流程草稿：新建（拿 id）或更新基本信息 + 保存整图 + 同步 draft 字段配置 */
export async function saveProcessDraft(def: ProcessDef): Promise<{ ok: true; id: number }> {
  let defId = def.id;
  if (!defId) {
    defId = await api.apiCreateDef({ code: def.defCode, name: def.defName, description: def.description });
  } else {
    await api.apiUpdateDef(defId, { name: def.defName, description: def.description });
  }
  await api.apiSaveGraph(defId, def.nodes, def.links);
  for (const [nodeKey, fields] of draftNodeFields.entries()) {
    if (fields.length > 0) {
      await api.apiSaveNodeFields(defId, nodeKey, fields);
    }
  }
  draftNodeFields.clear();
  return { ok: true as const, id: defId };
}

/** 发布流程（后端：版本+1 + 快照 + BPMN 编译部署）；新建流程自动先保存草稿 */
export async function publishProcess(def: ProcessDef): Promise<{ ok: true; version: number }> {
  let defId = def.id;
  if (!defId) {
    // 新建流程直接发布：先保存草稿（含 draft 节点字段配置同步），拿到 id 再发布
    const saved = await saveProcessDraft(def);
    defId = saved.id;
  }
  const version = await api.apiPublish(defId);
  return { ok: true as const, version };
}

// ==================== 维表（维度 → 分类 → 字段） ====================

export async function fetchDimensions(): Promise<Dimension[]> {
  return api.apiListDimensions();
}

export async function fetchCategories(dimensionId: number): Promise<DimensionCategory[]> {
  return api.apiListCategories(dimensionId);
}

/** 某分类下的字段（维表第三层） */
export async function fetchFields(categoryId: number): Promise<FieldDef[]> {
  return api.apiListFieldsByCategory(categoryId);
}

/** 全部字段（条件编辑器字段下拉；遍历所有分类汇总） */
export async function fetchAllFields(): Promise<FieldDef[]> {
  return api.apiListAllFields();
}

export async function createDimension(code: string, name: string): Promise<void> {
  await api.apiCreateDimension(code, name);
}

export async function createCategory(dimensionId: number, code: string, name: string): Promise<void> {
  await api.apiCreateCategory(dimensionId, code, name);
}

/** 分类下新建字段 */
export async function createField(
  categoryId: number,
  input: {
    fieldKey: string;
    fieldName: string;
    description?: string;
    fieldType: FieldDef['fieldType'];
    required: boolean;
    rule?: FieldDef['rule'];
  },
): Promise<void> {
  await api.apiCreateField(categoryId, input);
}

/** 更新字段（fieldKey 不可改） */
export async function updateField(
  id: number,
  input: {
    fieldName: string;
    description?: string;
    fieldType: FieldDef['fieldType'];
    required: boolean;
    rule?: FieldDef['rule'];
  },
): Promise<void> {
  await api.apiUpdateField(id, input);
}

/** 停用字段（逻辑删除，节点引用不失效） */
export async function deleteField(id: number): Promise<void> {
  await api.apiDeleteField(id);
}

// ==================== 节点字段 ====================

/** 某节点已配置字段（defId=0 走本地 draft 缓存） */
export async function fetchNodeFields(defId: number, nodeKey: string): Promise<NodeFormField[]> {
  if (defId <= 0) {
    return draftNodeFields.get(nodeKey) ?? [];
  }
  return api.apiListNodeFields(defId, nodeKey);
}

/** 保存节点字段配置（defId=0 暂存本地，保存草稿时同步） */
export async function saveNodeFields(
  defId: number,
  nodeKey: string,
  fields: NodeFormField[],
): Promise<{ ok: true }> {
  if (defId <= 0) {
    draftNodeFields.set(nodeKey, fields.map((f, i) => ({ ...f, sortNo: i + 1 })));
    return { ok: true as const };
  }
  await api.apiSaveNodeFields(defId, nodeKey, fields);
  return { ok: true as const };
}
