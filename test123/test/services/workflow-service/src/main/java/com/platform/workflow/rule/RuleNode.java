package com.platform.workflow.rule;

import java.util.List;

/**
 * 统一规则表达式树（AST）。
 *
 * 字段基础规则、条件必填/valueWhen、排他分支条件、外部 API 触发条件都使用同一棵树表达；
 * 配置端保存 JSON，运行端只消费本 AST 求值，不在业务 service 里散落 if/switch。
 */
public sealed interface RuleNode {

    /** 字段引用 */
    record Field(String fieldKey) implements RuleNode {}

    /** 常量（单值或 List） */
    record Const(Object value) implements RuleNode {}

    /** 比较：isEmpty/isNotEmpty 时 right 可为 null */
    record Compare(String op, RuleNode left, RuleNode right) implements RuleNode {}

    /** 逻辑组合：and / or */
    record Logic(String op, List<RuleNode> children) implements RuleNode {}

    /** 取反 */
    record Not(RuleNode child) implements RuleNode {}

    /** 受控函数：substr / len / number / lower / upper */
    record Func(String name, List<RuleNode> args) implements RuleNode {}
}
