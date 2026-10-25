package com.platform.workflow.rule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 规则树求值器。
 *
 * 只依赖 Map 上下文（fieldKey -> 当前值），不依赖数据库/Spring，便于单测与复用。
 */
public final class RuleEvaluator {

    private RuleEvaluator() {}

    public static boolean evaluate(RuleNode node, Map<String, Object> ctx) {
        return switch (node) {
            case RuleNode.Field f -> {
                Object v = ctx == null ? null : ctx.get(f.fieldKey());
                yield v instanceof Boolean b ? b : !isBlank(v);
            }
            case RuleNode.Const c -> Boolean.TRUE.equals(c.value());
            case RuleNode.Compare c -> {
                Object left = resolve(c.left(), ctx);
                Object right = c.right() == null ? null : resolve(c.right(), ctx);
                yield compare(c.op(), left, right);
            }
            case RuleNode.Logic l -> {
                boolean and = "and".equalsIgnoreCase(l.op());
                boolean acc = and;
                for (RuleNode child : l.children()) {
                    boolean v = evaluate(child, ctx);
                    acc = and ? (acc && v) : (acc || v);
                }
                yield acc;
            }
            case RuleNode.Not n -> !evaluate(n.child(), ctx);
            case RuleNode.Func f -> {
                List<Object> args = f.args().stream().map(a -> resolve(a, ctx)).toList();
                Object v = applyFunc(f.name(), args);
                yield v instanceof Boolean b ? b : !isBlank(v);
            }
        };
    }

    private static Object resolve(RuleNode node, Map<String, Object> ctx) {
        return switch (node) {
            case RuleNode.Field f -> ctx == null ? null : ctx.get(f.fieldKey());
            case RuleNode.Const c -> c.value();
            case RuleNode.Compare c -> evaluate(c, ctx);
            case RuleNode.Logic l -> evaluate(l, ctx);
            case RuleNode.Not n -> evaluate(n, ctx);
            case RuleNode.Func f -> applyFunc(f.name(), f.args().stream().map(a -> resolve(a, ctx)).toList());
        };
    }

    private static boolean isBlank(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof Collection<?> c) return c.isEmpty();
        if (v.getClass().isArray()) return java.lang.reflect.Array.getLength(v) == 0;
        return false;
    }

    private static boolean compare(String op, Object left, Object right) {
        return switch (op) {
            case "isEmpty" -> isBlank(left);
            case "isNotEmpty" -> !isBlank(left);
            case "eq" -> eq(left, right);
            case "neq" -> !eq(left, right);
            case "contains" -> String.valueOf(left == null ? "" : left).contains(String.valueOf(right == null ? "" : right));
            case "in" -> {
                if (right instanceof Collection<?> c) {
                    yield c.stream().anyMatch(o -> eq(left, o));
                } else if (right != null && right.getClass().isArray()) {
                    int n = java.lang.reflect.Array.getLength(right);
                    boolean hit = false;
                    for (int i = 0; i < n; i++) {
                        if (eq(left, java.lang.reflect.Array.get(right, i))) { hit = true; break; }
                    }
                    yield hit;
                } else {
                    yield eq(left, right);
                }
            }
            case "matches" -> {
                try {
                    yield Pattern.compile(String.valueOf(right)).matcher(String.valueOf(left == null ? "" : left)).matches();
                } catch (Exception e) {
                    yield false;
                }
            }
            case "between" -> {
                if (!(right instanceof java.util.Collection<?> col)) { yield false; }
                Object[] arr = col.toArray();
                if (arr.length < 2) { yield false; }
                Double nl = toNumber(left);
                Double nmin = toNumber(arr[0]);
                Double nmax = toNumber(arr[1]);
                yield nl != null && nmin != null && nmax != null && nl >= nmin && nl <= nmax;
            }
            case "gt", "gte", "lt", "lte" -> {
                Double nl = toNumber(left);
                Double nr = toNumber(right);
                if (nl == null || nr == null) yield false;
                yield switch (op) {
                    case "gt" -> nl > nr;
                    case "gte" -> nl >= nr;
                    case "lt" -> nl < nr;
                    default -> nl <= nr;
                };
            }
            default -> false;
        };
    }

    private static boolean eq(Object a, Object b) {
        Double na = toNumber(a);
        Double nb = toNumber(b);
        if (na != null && nb != null) return na.equals(nb);
        if (a instanceof Boolean || b instanceof Boolean) {
            return a instanceof Boolean ba && b instanceof Boolean bb && ba == bb;
        }
        return String.valueOf(a == null ? "" : a).equals(String.valueOf(b == null ? "" : b));
    }

    private static Double toNumber(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null || v instanceof Boolean) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object applyFunc(String name, List<Object> args) {
        String s0 = args.isEmpty() ? "" : String.valueOf(args.get(0));
        return switch (name) {
            case "substr" -> {
                int start = args.size() > 1 ? intVal(args.get(1)) : 0;
                if (start < 0 || start > s0.length()) yield "";
                Integer len = args.size() > 2 ? intOrNull(args.get(2)) : null;
                yield len == null ? s0.substring(start) : s0.substring(start, Math.min(s0.length(), start + len));
            }
            case "len" -> s0.length();
            case "number" -> toNumber(s0);
            case "lower" -> s0.toLowerCase();
            case "upper" -> s0.toUpperCase();
            default -> null;
        };
    }

    private static int intVal(Object v) {
        Integer n = intOrNull(v);
        return n == null ? 0 : n;
    }

    private static Integer intOrNull(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
