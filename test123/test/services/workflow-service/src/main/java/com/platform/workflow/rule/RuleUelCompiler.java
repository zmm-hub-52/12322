package com.platform.workflow.rule;

import java.util.List;
import java.util.Map;

/**
 * RuleNode -> Flowable UEL 表达式编译。
 *
 * 条件表达式只在独立 Compiler 中生成，业务/配置 service 不手拼 UEL 字符串。
 */
public final class RuleUelCompiler {

    private RuleUelCompiler() {}

    /** 编译为不含外层 ${} 的 UEL 表达式。 */
    public static String compile(RuleNode node, Map<String, String> fieldTypes) {
        return switch (node) {
            case RuleNode.Field f -> f.fieldKey();
            case RuleNode.Const c -> literal(c.value());
            case RuleNode.Compare c -> compileCompare(c, fieldTypes);
            case RuleNode.Logic l -> {
                String joiner = "and".equalsIgnoreCase(l.op()) ? " && " : " || ";
                String expr = l.children().stream()
                        .map(ch -> "(" + compile(ch, fieldTypes) + ")")
                        .reduce((a, b) -> a + joiner + b)
                        .orElse("true");
                yield expr;
            }
            case RuleNode.Not n -> "!(" + compile(n.child(), fieldTypes) + ")";
            case RuleNode.Func f -> compileFunc(f, fieldTypes);
        };
    }

    private static String compileCompare(RuleNode.Compare c, Map<String, String> fieldTypes) {
        String left = compile(c.left(), fieldTypes);
        String op = c.op();
        String right = c.right() == null ? null : compile(c.right(), fieldTypes);
        boolean numeric = c.left() instanceof RuleNode.Field f
                && "number".equals(fieldTypes.getOrDefault(f.fieldKey(), "input"));

        return switch (op) {
            case "contains" -> left + " != null && " + left + ".contains(" + right + ")";
            case "isEmpty" -> "(" + left + " == null || " + left + ".toString().isEmpty())";
            case "isNotEmpty" -> "(" + left + " != null && !" + left + ".toString().isEmpty())";
            case "in" -> compileIn(left, c.right(), numeric);
            case "matches" -> left + ".matches(" + right + ")";
            case "eq", "neq", "gt", "gte", "lt", "lte" -> {
                String symbol = switch (op) {
                    case "eq" -> " == ";
                    case "neq" -> " != ";
                    case "gt" -> " > ";
                    case "gte" -> " >= ";
                    case "lt" -> " < ";
                    default -> " <= ";
                };
                yield left + symbol + right;
            }
            default -> throw new IllegalArgumentException("未知比较符：" + op);
        };
    }

    private static String compileIn(String left, RuleNode right, boolean numeric) {
        Object value = right instanceof RuleNode.Const c ? c.value() : null;
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(v -> left + " == " + literalFor(v, numeric))
                    .reduce((a, b) -> a + " || " + b)
                    .orElse("false");
        }
        return left + " == " + literalFor(value, numeric);
    }

    private static String compileFunc(RuleNode.Func f, Map<String, String> fieldTypes) {
        List<String> args = f.args().stream().map(a -> compile(a, fieldTypes)).toList();
        return switch (f.name()) {
            case "substr" -> args.get(0) + ".substring(" + args.get(1) + ", " + args.get(1) + " + " + args.get(2) + ")";
            case "len" -> args.get(0) + ".length()";
            case "number" -> "Double.parseDouble(" + args.get(0) + ")";
            case "lower" -> args.get(0) + ".toLowerCase()";
            case "upper" -> args.get(0) + ".toUpperCase()";
            default -> throw new IllegalArgumentException("未知函数：" + f.name());
        };
    }

    private static String literal(Object v) {
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v == null) return "null";
        return "'" + String.valueOf(v).replace("'", "\\'") + "'";
    }

    private static String literalFor(Object v, boolean numeric) {
        if (numeric && v != null) return String.valueOf(v);
        return literal(v);
    }
}
