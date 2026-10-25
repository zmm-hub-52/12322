import type { ExprNode, RuleContext } from './types';

function isBlank(v: unknown): boolean {
  if (v === null || v === undefined) return true;
  if (typeof v === 'string') return v.trim() === '';
  if (Array.isArray(v)) return v.length === 0;
  return false;
}

function resolve(node: ExprNode, ctx: RuleContext): unknown {
  switch (node.kind) {
    case 'field':
      return ctx[node.fieldKey];
    case 'const':
      return node.value;
    case 'compare':
    case 'logic':
    case 'not':
      return evaluate(node, ctx);
    case 'func':
      return applyFunc(node.name, node.args.map((a) => resolve(a, ctx)));
    default:
      return undefined;
  }
}

function applyFunc(name: string, args: unknown[]): unknown {
  const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
  switch (name) {
    case 'substr': {
      const str = s(args[0]);
      const start = Number(args[1] ?? 0);
      const len = args[2] === undefined ? undefined : Number(args[2]);
      if (!Number.isFinite(start)) return '';
      return len === undefined || !Number.isFinite(len) ? str.slice(start) : str.slice(start, start + len);
    }
    case 'len':
      return s(args[0]).length;
    case 'number': {
      const n = Number(args[0]);
      return Number.isFinite(n) ? n : null;
    }
    case 'lower':
      return s(args[0]).toLowerCase();
    case 'upper':
      return s(args[0]).toUpperCase();
    default:
      return undefined;
  }
}

function toNumber(v: unknown): number | null {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'boolean') return null;
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function eq(a: unknown, b: unknown): boolean {
  const na = toNumber(a);
  const nb = toNumber(b);
  if (na !== null && nb !== null) return na === nb;
  if (typeof a === 'boolean' || typeof b === 'boolean') {
    if (typeof a === 'boolean' && typeof b === 'boolean') return a === b;
    return false;
  }
  return String(a ?? '') === String(b ?? '');
}

function compareValues(op: string, left: unknown, right: unknown): boolean {
  switch (op) {
    case 'isEmpty':
      return isBlank(left);
    case 'isNotEmpty':
      return !isBlank(left);
    case 'eq':
      return eq(left, right);
    case 'neq':
      return !eq(left, right);
    case 'contains':
      return String(left ?? '').includes(String(right ?? ''));
    case 'in': {
      const options = Array.isArray(right) ? right : [right];
      return options.some((o) => eq(left, o));
    }
    case 'matches': {
      try {
        return new RegExp(String(right ?? '')).test(String(left ?? ''));
      } catch {
        return false;
      }
    }
    case 'between': {
      const arr = Array.isArray(right) ? right : [];
      if (arr.length < 2) return false;
      const nl = toNumber(left);
      const nmin = toNumber(arr[0]);
      const nmax = toNumber(arr[1]);
      if (nl === null || nmin === null || nmax === null) return false;
      return nl >= nmin && nl <= nmax;
    }
    case 'gt':
    case 'gte':
    case 'lt':
    case 'lte': {
      const nl = toNumber(left);
      const nr = toNumber(right);
      if (nl === null || nr === null) return false;
      if (op === 'gt') return nl > nr;
      if (op === 'gte') return nl >= nr;
      if (op === 'lt') return nl < nr;
      return nl <= nr;
    }
    default:
      return false;
  }
}

/** 对一棵规则树求值，返回布尔结果。 */
export function evaluate(node: ExprNode, ctx: RuleContext = {}): boolean {
  switch (node.kind) {
    case 'field': {
      const v = ctx[node.fieldKey];
      return typeof v === 'boolean' ? v : !isBlank(v);
    }
    case 'const':
      return node.value === true;
    case 'compare': {
      const left = resolve(node.left, ctx);
      const right = node.right ? resolve(node.right, ctx) : undefined;
      return compareValues(node.op, left, right);
    }
    case 'logic': {
      if (node.op === 'and') return node.children.every((c) => evaluate(c, ctx));
      return node.children.some((c) => evaluate(c, ctx));
    }
    case 'not':
      return !evaluate(node.child, ctx);
    case 'func': {
      const v = applyFunc(node.name, node.args.map((a) => resolve(a, ctx)));
      return typeof v === 'boolean' ? v : !isBlank(v);
    }
    default:
      return false;
  }
}

/** 提取树中用到的字段 key（去重）。 */
export function collectFieldKeys(node: ExprNode, acc: Set<string> = new Set()): string[] {
  switch (node.kind) {
    case 'field':
      acc.add(node.fieldKey);
      break;
    case 'compare':
      collectFieldKeys(node.left, acc);
      if (node.right) collectFieldKeys(node.right, acc);
      break;
    case 'logic':
      node.children.forEach((c) => collectFieldKeys(c, acc));
      break;
    case 'not':
      collectFieldKeys(node.child, acc);
      break;
    case 'func':
      node.args.forEach((a) => collectFieldKeys(a, acc));
      break;
    case 'const':
      break;
  }
  return [...acc];
}

/** 常量值转稳定字符串（供错误消息/调试展示）。 */
export function describeValue(v: unknown): string {
  if (Array.isArray(v)) return '[' + v.map((x) => String(x)).join(', ') + ']';
  if (v === null) return 'null';
  return String(v);
}
