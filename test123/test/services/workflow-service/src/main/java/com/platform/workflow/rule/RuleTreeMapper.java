package com.platform.workflow.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON(Map) <-> RuleNode 映射器。
 *
 * 兼容前端 @platform/rule-engine 的 AST 结构：
 * {kind:'field'|'const'|'compare'|'logic'|'not'|'func', ...}
 */
public final class RuleTreeMapper {

    private RuleTreeMapper() {}

    @SuppressWarnings("unchecked")
    public static RuleNode parse(Object obj) {
        if (obj instanceof Map<?, ?> m) {
            return parseMap((Map<String, Object>) m);
        }
        throw new IllegalArgumentException("规则树必须是 JSON 对象");
    }

    @SuppressWarnings("unchecked")
    public static RuleNode parseMap(Map<String, Object> m) {
        String kind = str(m.get("kind"));
        return switch (kind) {
            case "field" -> new RuleNode.Field(str(m.get("fieldKey")));
            case "const" -> new RuleNode.Const(m.get("value"));
            case "compare" -> {
                RuleNode left = parse(m.get("left"));
                RuleNode right = m.get("right") == null ? null : parse(m.get("right"));
                yield new RuleNode.Compare(str(m.get("op")), left, right);
            }
            case "logic" -> {
                Object children = m.get("children");
                List<RuleNode> list = new ArrayList<>();
                if (children instanceof List<?> l) {
                    for (Object c : l) list.add(parse(c));
                }
                yield new RuleNode.Logic(str(m.get("op")), list);
            }
            case "not" -> new RuleNode.Not(parse(m.get("child")));
            case "func" -> {
                Object args = m.get("args");
                List<RuleNode> list = new ArrayList<>();
                if (args instanceof List<?> l) {
                    for (Object c : l) list.add(parse(c));
                }
                yield new RuleNode.Func(str(m.get("name")), list);
            }
            default -> throw new IllegalArgumentException("未知规则节点类型：" + kind);
        };
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
