import {
  and, cmp, condition, constant, enumRule, field, func, not, or, patternRule, range, required,
  type ExprNode,
} from '@platform/rule-engine';
import type { SchemaField } from '../types/schema';

const TRUE = constant(true);

function compileCodeRange(selfKey: string, r: Record<string, unknown>): ExprNode {
  const prefix = String(r.prefix ?? '');
  const width = Number(r.width ?? 0);
  const parts: ExprNode[] = [];
  if (prefix.length > 0) {
    parts.push(cmp('eq', func('substr', field(selfKey), constant(0), constant(prefix.length)), constant(prefix)));
  }
  if (width > 0) {
    parts.push(cmp('eq', func('len', field(selfKey)), constant(prefix.length + width)));
    const num = func('number', func('substr', field(selfKey), constant(prefix.length), constant(width)));
    if (r.start !== undefined) parts.push(cmp('gte', num, constant(Number(r.start))));
    if (r.end !== undefined) parts.push(cmp('lte', num, constant(Number(r.end))));
  }
  return parts.length ? and(...parts) : TRUE;
}

function compileValueRule(rule: Record<string, unknown>, selfKey: string): ExprNode {
  const type = String(rule.type ?? '');
  switch (type) {
    case 'enum':
      return enumRule(selfKey, Array.isArray(rule.options) ? (rule.options as string[]) : []);
    case 'range':
      return range(selfKey, rule.min as number | string | undefined, rule.max as number | string | undefined);
    case 'pattern':
      return patternRule(selfKey, String(rule.pattern ?? ''));
    case 'codeRange':
      return compileCodeRange(selfKey, rule);
    default:
      return TRUE;
  }
}

function compileRequiredWhen(rule: Record<string, unknown>, selfKey: string): ExprNode {
  const trigger = condition(String(rule.fieldKey ?? ''), String(rule.operator ?? 'eq'), String(rule.value ?? ''));
  const parts: ExprNode[] = [or(not(trigger), required(selfKey))];
  const inner = rule.rule as Record<string, unknown> | undefined;
  if (inner && Object.keys(inner).length > 0) {
    parts.push(or(not(trigger), compileValueRule(inner, selfKey)));
  }
  return and(...parts);
}

function compileValueWhen(rule: Record<string, unknown>, selfKey: string): ExprNode {
  const cases = Array.isArray(rule.cases) ? (rule.cases as Record<string, unknown>[]) : [];
  return and(...cases.map((c) => {
    const cond = condition(String(c.fieldKey ?? ''), String(c.operator ?? 'eq'), String(c.value ?? ''));
    return or(not(cond), cmp('eq', field(selfKey), constant(String(c.expect ?? ''))));
  }));
}

/** 只编译必填类规则（required + requiredWhen），空值校验用。 */
export function compileRequiredRule(f: SchemaField): ExprNode {
  const parts: ExprNode[] = [];
  if (f.required) parts.push(required(f.key));
  const r = f.rule as Record<string, unknown> | undefined;
  if (r && String(r.type ?? '') === 'requiredWhen') {
    const trigger = condition(String(r.fieldKey ?? ''), String(r.operator ?? 'eq'), String(r.value ?? ''));
    parts.push(or(not(trigger), required(f.key)));
  }
  return parts.length === 0 ? TRUE : and(...parts);
}

/** 把一列定义编译成可执行规则树（列规则 + 必填）。 */
export function compileFieldRule(f: SchemaField): ExprNode {
  const parts: ExprNode[] = [];
  if (f.required) parts.push(required(f.key));
  const r = f.rule as Record<string, unknown> | undefined;
  if (r && Object.keys(r).length > 0) {
    const type = String(r.type ?? '');
    if (type === 'tree') {
      parts.push(r.tree as ExprNode);
    } else if (type === 'requiredWhen') {
      parts.push(compileRequiredWhen(r, f.key));
    } else if (type === 'valueWhen') {
      parts.push(compileValueWhen(r, f.key));
    } else {
      parts.push(compileValueRule(r, f.key));
    }
  }
  return parts.length === 0 ? TRUE : parts.length === 1 ? parts[0] : and(...parts);
}
