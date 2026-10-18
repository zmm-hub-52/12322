/**
 * workflow-admin 前端类型定义（与后端 field_def / process_node 等表结构对齐）
 */

/** 字段业务类型（中性业务语义，渲染时由前端映射 iX 组件） */
export type FieldType = 'input' | 'textarea' | 'number' | 'select' | 'date' | 'boolean';

/** 字段展示方式（节点级） */
export type FieldMode = 'editable' | 'readonly' | 'hidden';

/** 取值规则（可视化编辑器自动序列化 JSON，用户不手写） */
export type ValueRule =
  | { type: 'enum'; options: string[] }
  | { type: 'codeRange'; prefix: string; start: number; end: number; width: number }
  | { type: 'range'; min: number; max: number; decimals?: number };

/** 校验规则 */
export interface ValidationRule {
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
}

/** 字段定义（v3：挂在维度分类下——维表第三层就是字段） */
export interface FieldDef {
  id: number;
  /** 所属维度分类 */
  categoryId?: number;
  fieldKey: string;
  fieldName: string;
  fieldType: FieldType;
  defaultValue?: string;
  placeholder?: string;
  valueRule?: ValueRule;
  validation?: ValidationRule;
  enabled: boolean;
}

/**
 * 节点类型：start 开始 / fill 填写 / approval 审批 /
 * parallelSplit/parallelJoin 并行分叉/汇合 / exclusiveSplit/exclusiveJoin 排他分叉/汇合 / end 结束
 */
export type NodeType =
  | 'start'
  | 'fill'
  | 'approval'
  | 'parallelSplit'
  | 'parallelJoin'
  | 'exclusiveSplit'
  | 'exclusiveJoin'
  | 'end';

/** 审批驳回策略：back_prev 退回上一步 / back_starter 驳回到发起人 / terminate 终止流程 */
export type RejectStrategy = 'back_prev' | 'back_starter' | 'terminate';

/** 流程节点 */
export interface ProcessNode {
  id: number;
  nodeKey: string;
  nodeName: string;
  nodeType: NodeType;
  /** 审批节点驳回策略（默认 back_prev） */
  rejectStrategy?: RejectStrategy;
}

/** 排他分支条件（可视化编辑器自动序列化，用户零表达式语言） */
export interface BranchCondition {
  /** 条件字段（本流程表单字段） */
  fieldKey: string;
  fieldName: string;
  /** 比较符：gt/gte/lt/lte/eq/neq/contains */
  operator: string;
  /** 比较值 */
  value: string;
  /** 是否为默认支（所有条件都不满足时走这条） */
  isDefault?: boolean;
}

/** 流程连线（有向边，DAG） */
export interface ProcessLink {
  id: number;
  fromKey: string;
  toKey: string;
  /** 排他分叉出边上的条件（并行边无） */
  condition?: BranchCondition;
  /** 同源出边顺序（排他条件匹配优先级） */
  sortNo?: number;
}

/** 节点字段（节点上配置的字段，含节点级覆盖） */
export interface NodeFormField {
  id: number;
  fieldKey: string;
  fieldName: string;
  fieldType: FieldType;
  mode: FieldMode;
  required: boolean;
  valueRule?: ValueRule;
  validation?: ValidationRule;
  placeholder?: string;
  sortNo: number;
}

/** 流程定义 */
export interface ProcessDef {
  id: number;
  defCode: string;
  defName: string;
  description: string;
  /** 发起角色（选项式） */
  initiator: string;
  /** 当前版本号（发布一次 +1） */
  version: number;
  /** 是否已发布过（false = 纯草稿） */
  published: boolean;
  /** 节点集合（DAG） */
  nodes: ProcessNode[];
  /** 连线集合 */
  links: ProcessLink[];
}

/** 维表三层：维度 → 分类 → 字段（字段即第三层） */
export interface Dimension {
  id: number;
  dimensionCode: string;
  dimensionName: string;
}

export interface DimensionCategory {
  id: number;
  dimensionId: number;
  categoryCode: string;
  categoryName: string;
}

/** 流程模式模板（向导第 2 步） */
export interface ProcessTemplate {
  key: string;
  name: string;
  description: string;
  nodes: Pick<ProcessNode, 'nodeKey' | 'nodeName' | 'nodeType'>[];
  links: ProcessLink[];
}
