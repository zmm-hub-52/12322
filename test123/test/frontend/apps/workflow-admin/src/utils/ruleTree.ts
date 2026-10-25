import {
  and, cmp, condition, constant, enumRule, field, func, not, or, patternRule, range, required,
  type ExprNode,
} from '@platform/rule-engine';
import type { Rule, ValueRule } from '../types/schema';

const TRUE = constant(true);

/** 单条件可视化配置 -> AST 叶子节点 */
export function compileCondition(cond: { fieldKey: string; operator: string; value: string }): ExprNode {
  return condition(cond.fieldKey, cond.operator, cond.value);
}

/** codeRange：前缀 + 定长数字段 + 起止号。用 substr/len/number 函数表达，不硬编码正则 */
function compileCodeRange(selfKey: string, rule: ValueRule & { type: 'codeRange' }): ExprNode {
  const prefix = rule.prefix ?? '';
  const width = rule.width ?? 0;
  const parts: ExprNode[] = [];
  if (prefix.length > 0) {
    parts.push(cmp('eq', func('substr', field(selfKey), constant(0), constant(prefix.length)), constant(prefix)));
  }
  if (width > 0) {
    parts.push(cmp('eq', func('len', field(selfKey)), constant(prefix.length + width)));
    const num = func('number', func('substr', field(selfKey), constant(prefix.length), constant(width)));
    if (rule.start !== undefined) parts.push(cmp('gte', num, constant(rule.start)));
    if (rule.end !== undefined) parts.push(cmp('lte', num, constant(rule.end)));
  }
  return parts.length ? and(...parts) : TRUE;
}

/** 取值约束（enum/codeRange/range/pattern）-> AST */
export function compileValueRule(rule: ValueRule, selfKey: string): ExprNode {
  switch (rule.type) {
    case 'enum':
      return enumRule(selfKey, rule.options ?? []);
    case 'range':
      return range(selfKey, rule.min, rule.max);
    case 'pattern':
      return patternRule(selfKey, rule.pattern ?? '');
    case 'codeRange':
      return compileCodeRange(selfKey, rule);
    default:
      return TRUE;
  }
}

/** requiredWhen：触发时必填（并可选附带内层取值约束）。if trigger then X 等价于 !trigger or X */
export function compileRequiredWhen(
  rule: { fieldKey: string; operator: string; value: string; rule?: ValueRule },
  selfKey: string,
): ExprNode {
  const trigger = compileCondition({ fieldKey: rule.fieldKey, operator: rule.operator, value: rule.value });
  const parts: ExprNode[] = [or(not(trigger), required(selfKey))];
  if (rule.rule) {
    parts.push(or(not(trigger), compileValueRule(rule.rule, selfKey)));
  }
  return and(...parts);
}

/** valueWhen：命中第 i 条 case 时字段必须等于 expect；全部 case 都不命中则无约束 */
export function compileValueWhen(
  rule: { cases: { fieldKey: string; operator: string; value: string; expect: string }[] },
  selfKey: string,
): ExprNode {
  const cases = rule.cases ?? [];
  return and(...cases.map((c) => or(not(compileCondition(c)), cmp('eq', field(selfKey), constant(c.expect)))));
}

/** 把字段定义（required + rule）编译成一棵可执行规则树 */
export function compileFieldRule(params: { selfKey: string; required?: boolean; rule?: Rule }): ExprNode {
  const parts: ExprNode[] = [];
  if (params.required) parts.push(required(params.selfKey));
  const r = params.rule;
  if (r) {
    switch (r.type) {
      case 'enum':
      case 'codeRange':
      case 'range':
      case 'pattern':
        parts.push(compileValueRule(r, params.selfKey));
        break;
      case 'requiredWhen':
        parts.push(compileRequiredWhen(r, params.selfKey));
        break;
      case 'valueWhen':
        parts.push(compileValueWhen(r, params.selfKey));
        break;
      case 'tree':
        parts.push(r.tree);
        break;
    }
  }
  return parts.length === 0 ? TRUE : parts.length === 1 ? parts[0] : and(...parts);
}
