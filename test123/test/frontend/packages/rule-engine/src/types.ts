/**
 * 统一规则表达式树（AST）。
 *
 * 设计目标：字段基础规则、条件必填/valueWhen、排他分支条件、外部 API 触发条件
 * 都使用同一棵树表达，配置端只维护「可视化配置」，保存时编译为本 AST；
 * 运行端只消费本 AST 求值，不再散落 if/switch 判断。
 */

export type Scalar = string | number | boolean | null;

/** const 节点可承载单值或数组（in 运算符的候选集合） */
export type ConstValue = Scalar | Scalar[];

export type CompareOp =
  | 'eq'
  | 'neq'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'contains'
  | 'isEmpty'
  | 'isNotEmpty'
  | 'in'
  | 'matches'
  | 'between';

export type LogicOp = 'and' | 'or';

/** 可参与表达式树的函数（求值器按名称分派，不引入任意脚本） */
export type FuncName = 'substr' | 'len' | 'number' | 'lower' | 'upper';

export type ExprNode =
  | { kind: 'field'; fieldKey: string }
  | { kind: 'const'; value: ConstValue }
  | { kind: 'compare'; op: CompareOp; left: ExprNode; right?: ExprNode }
  | { kind: 'logic'; op: LogicOp; children: ExprNode[] }
  | { kind: 'not'; child: ExprNode }
  | { kind: 'func'; name: FuncName; args: ExprNode[] };

/** 求值上下文：fieldKey -> 当前行/当前表单的值 */
export type RuleContext = Record<string, unknown>;
