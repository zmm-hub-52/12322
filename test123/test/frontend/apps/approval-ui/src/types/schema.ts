/**
 * approval-ui 使用端类型（与后端快照字段结构对齐）
 */

/** 快照字段（后端 SchemaAssembler 展开后的结构） */
export interface SchemaField {
  key: string;
  label: string;
  type: string; // input/textarea/number/select/date/boolean
  mode: 'editable' | 'readonly' | 'hidden';
  required: boolean;
  valueRule?: {
    type?: string;
    options?: string[];
    min?: number;
    max?: number;
    [k: string]: unknown;
  };
  validation?: {
    required?: boolean;
    minLength?: number;
    maxLength?: number;
    pattern?: string;
  };
  defaultValue?: unknown;
  placeholder?: string | null;
  rule?: unknown;
}

/** 已发布流程（列表展示） */
export interface PublishedDef {
  id: number;
  code: string;
  name: string;
  version: number;
  description: string | null;
}

/** 流程节点（选审批人用） */
export interface ProcessNodeView {
  id: number;
  nodeKey: string;
  nodeName: string;
  nodeType: string;
  rejectStrategy?: string;
}

/** 图 */
export interface GraphView {
  nodes: ProcessNodeView[];
  links: { id: number; fromKey: string; toKey: string; condition?: unknown; sortNo?: number }[];
}

/** 用户（选审批人） */
export interface UserView {
  id: number;
  username: string;
  name: string;
  roleCode: string;
  roleName: string;
}

/** 待办任务 */
export interface TaskView {
  taskId: string;
  instanceId: number;
  bizNo: string | null;
  title: string | null;
  nodeKey: string;
  nodeName: string;
  nodeType: string | null;
  createTime: string | null;
}

/** 当前节点 schema（办理页） */
export interface NodeSchema {
  schemaVersion: string;
  taskId?: string;
  nodeKey: string;
  nodeName?: string;
  nodeType: string | null;
  fields?: SchemaField[];
  formData: Record<string, unknown>;
  finished: boolean;
  endActivityId?: string | null;
}

/** 轨迹记录 */
export interface RecordView {
  id: number;
  processInstanceId: number;
  nodeKey: string;
  nodeName: string | null;
  actionType: string; // start/submit/approve/reject/terminate
  executor: string | null;
  comment: string | null;
  createTime: string;
}

/** Excel 导入预览（上传 → 校验 → 逐行发起） */
export interface ImportRow {
  rowNo: number;
  data: Record<string, string>;
  valid: boolean;
  errors: string[];
  /** 前端标记：该行是否已发起（预览表格状态） */
  started?: boolean;
}

export interface ImportPreview {
  keys: string[];
  rows: ImportRow[];
}
