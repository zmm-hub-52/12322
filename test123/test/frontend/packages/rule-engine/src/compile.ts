import type { CompareOp, ConstValue, ExprNode, FuncName, Scalar } from './types';

export const field = (fieldKey: string): ExprNode => ({ kind: 'field', fieldKey });
export const constant = (value: ConstValue): ExprNode => ({ kind: 'const', value });
export const cmp = (op: CompareOp, left: ExprNode, right?: ExprNode): ExprNode => ({ kind: 'compare', op, left, right });
export const and = (...children: ExprNode[]): ExprNode => ({ kind: 'logic', op: 'and', children });
export const or = (...children: ExprNode[]): ExprNode => ({ kind: 'logic', op: 'or', children });
export const not = (child: ExprNode): ExprNode => ({ kind: 'not', child });
export const func = (name: FuncName, ...args: ExprNode[]): ExprNode => ({ kind: 'func', name, args });

/** required：字段非空 */
export const required = (fieldKey: string): ExprNode => not(cmp('isEmpty', field(fieldKey)));

/** range：min <= field <= max（min/max 缺省的一侧不参与约束） */
export const range = (fieldKey: string, min?: Scalar, max?: Scalar): ExprNode => {
  const parts: ExprNode[] = [];
  if (min !== undefined && min !== null && min !== '') {
    parts.push(cmp('gte', field(fieldKey), constant(min)));
  }
  if (max !== undefined && max !== null && max !== '') {
    parts.push(cmp('lte', field(fieldKey), constant(max)));
  }
  return parts.length === 0 ? constant(true) : and(...parts);
};

/** enum：字段值属于候选集合 */
export const enumRule = (fieldKey: string, options: Scalar[]): ExprNode =>
  cmp('in', field(fieldKey), constant(options));

/** pattern：字段值匹配正则 */
export const patternRule = (fieldKey: string, pattern: string): ExprNode =>
  cmp('matches', field(fieldKey), constant(pattern));

/** 可视化比较符 -> AST 比较符（前端 legacy 字符串归一化） */
export function normalizeCompareOp(op: string): CompareOp {
  switch (op) {
    case '=':
      return 'eq';
    case '!=':
      return 'neq';
    case '>':
      return 'gt';
    case '>=':
      return 'gte';
    case '<':
      return 'lt';
    case '<=':
      return 'lte';
    case 'eq':
    case 'neq':
    case 'gt':
    case 'gte':
    case 'lt':
    case 'lte':
    case 'contains':
    case 'isEmpty':
    case 'isNotEmpty':
    case 'in':
    case 'matches':
      return op;
    default:
      return 'eq';
  }
}

/** 单条件可视化配置 -> AST 叶子节点 */
export function condition(fieldKey: string, operator: string, value: Scalar): ExprNode {
  const op = normalizeCompareOp(operator);
  if (op === 'isEmpty' || op === 'isNotEmpty') {
    return cmp(op, field(fieldKey));
  }
  return cmp(op, field(fieldKey), constant(value));
}
