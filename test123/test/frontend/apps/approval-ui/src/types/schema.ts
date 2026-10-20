/**
 * approval-ui 使用端类型（与后端快照字段结构对齐）
 */

/** 取值约束（单条，四选一） */
export interface ValueRule {
  type?: 'enum' | 'codeRange' | 'range' | 'pattern';
  options?: string[];
  prefix?: string;
  start?: number;
  end?: number;
  width?: number;
  min?: number;
  max?: number;
  decimals?: number;
  pattern?: string;
  [k: string]: unknown;
}

/** 快照字段（后端 SchemaAssembler 展开后的结构） */
export interface SchemaField {
  key: string;
  label: string;
  /** 字段描述（填写时 placeholder：字段名|字段描述） */
  description?: string | null;
  type: string; // input/textarea/number/select/date
  mode: 'editable' | 'readonly' | 'hidden';
  required: boolean;
  /** 字段规则（取值+校验二合一） */
  rule?: {
    type?: 'enum' | 'codeRange' | 'range' | 'pattern' | 'requiredWhen' | 'valueWhen';
    options?: string[];
    prefix?: string;
    start?: number;
    end?: number;
    width?: number;
    min?: number;
    max?: number;
    decimals?: number;
    pattern?: string;
    fieldKey?: string;
    operator?: string;
    value?: string;
    cases?: { fieldKey: string; operator: string; value: string; expect: string }[];
    /** requiredWhen 附带的内层取值约束（单条） */
    rule?: ValueRule;
    [k: string]: unknown;
  };
  placeholder?: string | null;
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
  assigneeMode?: 'fixed' | 'dynamic';
  fixedAssignee?: string | null;
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

/** 已完结流程实例 */
export interface FinishedInstance {
  id: number;
  bizNo: string | null;
  title: string | null;
  applicant: string | null;
  processName: string | null;
  createTime: string;
  endTime: string | null;
  status: 'finished' | 'terminated';
}

/** 下一用户任务（办理页「下一处理人」选择） */
export interface NextNode {
  nodeKey: string;
  nodeName: string;
  nodeType: string;
  assigneeMode: 'fixed' | 'dynamic';
  fixedAssignee?: string | null;
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
  /** 下一用户任务列表（串行 1 个，并行多支） */
  nextNodes?: NextNode[];
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
