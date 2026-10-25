package com.platform.workflow.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把历史扁平规则配置编译成 RuleNode。
 *
 * 旧配置（字段 schema 的 required + rule，rule 为 enum/codeRange/range/pattern/
 * requiredWhen/valueWhen）在这里统一编译为树；新配置 {type:'tree'} 直接解析。
 */
public final class RuleFactory {

    private RuleFactory() {}

    public static RuleNode field(String key) { return new RuleNode.Field(key); }
    public static RuleNode constNode(Object v) { return new RuleNode.Const(v); }
    public static RuleNode compare(String op, RuleNode left, RuleNode right) { return new RuleNode.Compare(op, left, right); }
    public static RuleNode and(List<RuleNode> parts) { return new RuleNode.Logic("and", parts); }
    public static RuleNode and(RuleNode... parts) { return new RuleNode.Logic("and", List.of(parts)); }
    public static RuleNode or(RuleNode... parts) { return new RuleNode.Logic("or", List.of(parts)); }
    public static RuleNode not(RuleNode child) { return new RuleNode.Not(child); }
    public static RuleNode func(String name, RuleNode... args) { return new RuleNode.Func(name, List.of(args)); }

    public static RuleNode required(String key) { return not(compare("isEmpty", field(key), null)); }
    public static RuleNode TRUE() { return constNode(true); }

    /** 字段 schema 中的 required + rule 编译为一棵树。 */
    @SuppressWarnings("unchecked")
    public static RuleNode compileField(Map<String, Object> field, String selfKey) {
        List<RuleNode> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(field.get("required"))) {
            parts.add(required(selfKey));
        }
        Object ruleObj = field.get("rule");
        RuleNode rule = compileRule(ruleObj, selfKey);
        if (!isTrue(rule)) parts.add(rule);
        return parts.isEmpty() ? TRUE() : (parts.size() == 1 ? parts.get(0) : and(parts));
    }

    /** 仅 required + requiredWhen 触发条件编译（空值校验用）。 */
    @SuppressWarnings("unchecked")
    public static RuleNode compileRequiredRule(Map<String, Object> field, String selfKey) {
        List<RuleNode> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(field.get("required"))) {
            parts.add(required(selfKey));
        }
        Map<String, Object> rule = toMap(field.get("rule"));
        if (rule != null && "requiredWhen".equals(str(rule.get("type")))) {
            parts.add(or(not(condition(rule)), required(selfKey)));
        }
        return parts.isEmpty() ? TRUE() : (parts.size() == 1 ? parts.get(0) : and(parts));
    }

    /** 仅取值/条件取值校验编译（非空时用；tree 模式整体返回树）。 */
    public static RuleNode compileValueRuleTree(Map<String, Object> field, String selfKey) {
        Map<String, Object> rule = toMap(field.get("rule"));
        if (rule == null || rule.isEmpty()) return TRUE();
        String type = str(rule.get("type"));
        if ("requiredWhen".equals(type)) {
            Object inner = rule.get("rule");
            if (toMap(inner) == null || toMap(inner).isEmpty()) return TRUE();
            RuleNode trigger = condition(rule);
            return or(not(trigger), compileRule(inner, selfKey));
        }
        return compileRule(rule, selfKey);
    }

    /** rule 对象（Map / 字符串）-> RuleNode。 */
    @SuppressWarnings("unchecked")
    public static RuleNode compileRule(Object ruleObj, String selfKey) {
        if (ruleObj == null) return TRUE();
        Map<String, Object> rule = toMap(ruleObj);
        if (rule == null || rule.isEmpty()) return TRUE();
        String type = str(rule.get("type"));
        return switch (type) {
            case "enum" -> enumRule(selfKey, (List<Object>) rule.getOrDefault("options", List.of()));
            case "codeRange" -> codeRange(selfKey, rule);
            case "range" -> range(selfKey, rule);
            case "pattern" -> compare("matches", field(selfKey), constNode(rule.getOrDefault("pattern", "")));
            case "requiredWhen" -> requiredWhen(rule, selfKey);
            case "valueWhen" -> valueWhen(rule, selfKey);
            case "tree" -> RuleTreeMapper.parse(rule.get("tree"));
            default -> TRUE();
        };
    }

    /** 单条件 Map -> Compare 节点。 */
    public static RuleNode condition(Map<String, Object> cond) {
        String key = str(cond.get("fieldKey"));
        String op = str(cond.getOrDefault("operator", "eq"));
        Object value = cond.get("value");
        if ("isEmpty".equals(op) || "isNotEmpty".equals(op)) {
            return compare(op, field(key), null);
        }
        return compare(op, field(key), constNode(value == null ? "" : value));
    }

    @SuppressWarnings("unchecked")
    private static RuleNode requiredWhen(Map<String, Object> rule, String selfKey) {
        RuleNode trigger = condition(rule);
        List<RuleNode> parts = new ArrayList<>();
        parts.add(or(not(trigger), required(selfKey)));
        Object innerObj = rule.get("rule");
        Map<String, Object> inner = toMap(innerObj);
        if (inner != null && !inner.isEmpty()) {
            parts.add(or(not(trigger), compileRule(inner, selfKey)));
        }
        return and(parts);
    }

    @SuppressWarnings("unchecked")
    private static RuleNode valueWhen(Map<String, Object> rule, String selfKey) {
        Object casesObj = rule.get("cases");
        List<Object> cases = casesObj instanceof List<?> l ? (List<Object>) l : List.of();
        List<RuleNode> parts = new ArrayList<>();
        for (Object c : cases) {
            Map<String, Object> cond = toMap(c);
            RuleNode expect = constNode(cond == null ? "" : cond.getOrDefault("expect", ""));
            parts.add(or(not(condition(cond == null ? Map.of() : cond)), compare("eq", field(selfKey), expect)));
        }
        return parts.isEmpty() ? TRUE() : and(parts);
    }

    private static RuleNode enumRule(String selfKey, List<Object> options) {
        return compare("in", field(selfKey), constNode(options));
    }

    private static RuleNode range(String selfKey, Map<String, Object> rule) {
        List<RuleNode> parts = new ArrayList<>();
        Object min = rule.get("min");
        Object max = rule.get("max");
        if (min != null) parts.add(compare("gte", field(selfKey), constNode(min)));
        if (max != null) parts.add(compare("lte", field(selfKey), constNode(max)));
        return parts.isEmpty() ? TRUE() : and(parts);
    }

    private static RuleNode codeRange(String selfKey, Map<String, Object> rule) {
        String prefix = str(rule.getOrDefault("prefix", ""));
        int width = intVal(rule.get("width"));
        List<RuleNode> parts = new ArrayList<>();
        if (!prefix.isEmpty()) {
            parts.add(compare("eq",
                    func("substr", field(selfKey), constNode(0), constNode(prefix.length())),
                    constNode(prefix)));
        }
        if (width > 0) {
            parts.add(compare("eq", func("len", field(selfKey)), constNode(prefix.length() + width)));
            RuleNode num = func("number", func("substr", field(selfKey), constNode(prefix.length()), constNode(width)));
            Object start = rule.get("start");
            Object end = rule.get("end");
            if (start != null) parts.add(compare("gte", num, constNode(start)));
            if (end != null) parts.add(compare("lte", num, constNode(end)));
        }
        return parts.isEmpty() ? TRUE() : and(parts);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private static boolean isTrue(RuleNode n) {
        return n instanceof RuleNode.Const c && Boolean.TRUE.equals(c.value());
    }

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }

    private static int intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
    }
}
