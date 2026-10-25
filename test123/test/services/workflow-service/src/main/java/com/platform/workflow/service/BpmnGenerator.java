package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.dto.ConditionDto;
import com.platform.workflow.entity.FieldDef;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.entity.ProcessLink;
import com.platform.workflow.entity.ProcessNode;
import com.platform.workflow.mapper.FieldDefMapper;
import com.platform.workflow.rule.RuleNode;
import com.platform.workflow.rule.RuleUelCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BPMN 生成器（v2 核心）：把配置图（节点 + 连线）编译成 BPMN 2.0 XML。
 *
 * 编译规则（与前端画布约定一一对应）：
 * - node_key 即 BPMN 元素 id（userTask / gateway / event），三合一 key；
 * - start → startEvent；end → endEvent；fill/approval → userTask；
 * - parallelSplit/Join → parallelGateway；exclusiveSplit/Join → exclusiveGateway；
 * - 排他分叉出边：condition_json → conditionExpression（默认边无表达式，放最后）；
 * - 审批节点两条出线：${approved} 走批准线、${!approved} 走驳回线（目标按驳回策略：
 *   back_prev 指向前驱 / back_starter 指向第一个 fill / terminate 指向终止结束事件）。
 *
 * 生成的 XML 由发布服务经 RepositoryService 部署给 Flowable（见 ProcessConfigService.publish）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnGenerator {

    private final FieldDefMapper fieldDefMapper;
    private final ObjectMapper objectMapper;

    /** BPMN 元素 id 合法性（node_key 约束） */
    private static final Pattern XML_ID = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*$");

    /**
     * 生成 BPMN 2.0 XML。
     *
     * @param def   流程定义
     * @param nodes 节点列表（nodeKey 即元素 id）
     * @param links 连线列表（含排他条件）
     * @return BPMN XML 字符串
     */
    public String generate(ProcessDef def, List<ProcessNode> nodes, List<ProcessLink> links) {
        // 1) 校验 process id（= code）与 node_key 合法性（BPMN 元素 id = NCName，必须字母/下划线开头）
        if (!XML_ID.matcher(def.getCode()).matches()) {
            throw new BizException("流程编码不能用作 BPMN process id（需字母/下划线开头，无空格）：" + def.getCode());
        }
        for (ProcessNode n : nodes) {
            if (!XML_ID.matcher(n.getNodeKey()).matches()) {
                throw new BizException("节点 key 不能用作 BPMN id（需字母/下划线开头，无空格）：" + n.getNodeKey());
            }
        }

        // 2) 字段类型映射（条件表达式：number 不加引号，其余加引号）
        Map<String, String> fieldTypes = fieldDefMapper.selectList(
                        new LambdaQueryWrapper<FieldDef>().eq(FieldDef::getEnabled, true))
                .stream()
                .collect(Collectors.toMap(FieldDef::getFieldKey, FieldDef::getFieldType, (a, b) -> a));

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        sb.append("             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n");
        sb.append("             targetNamespace=\"http://platform.com/workflow\">\n");
        sb.append("  <process id=\"").append(xmlEscape(def.getCode()))
                .append("\" name=\"").append(xmlEscape(def.getName())).append("\">\n");

        // 3) 元素
        for (ProcessNode n : nodes) {
            appendElement(sb, n);
        }

        // 4) 连线（sequenceFlow）
        appendFlows(sb, nodes, links, fieldTypes);

        sb.append("  </process>\n");
        sb.append("</definitions>\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 元素生成
    // ------------------------------------------------------------------

    private void appendElement(StringBuilder sb, ProcessNode n) {
        String key = n.getNodeKey();
        String name = xmlEscape(n.getNodeName());
        switch (n.getNodeType()) {
            case "start" -> sb.append("    <startEvent id=\"").append(key).append("\" name=\"").append(name).append("\"/>\n");
            case "end" -> sb.append("    <endEvent id=\"").append(key).append("\" name=\"").append(name).append("\"/>\n");
            case "fill", "approval" -> {
                if ("role".equals(n.getAssigneeMode()) && n.getRoleCode() != null && !n.getRoleCode().isBlank()) {
                    sb.append("    <userTask id=\"").append(key).append("\" name=\"").append(name)
                            .append("\" flowable:candidateGroups=\"").append(xmlEscape(n.getRoleCode())).append("\"/>\n");
                } else {
                    sb.append("    <userTask id=\"").append(key).append("\" name=\"").append(name)
                            .append("\" flowable:assignee=\"${assignee_").append(key).append("}\"/>\n");
                }
            }
            case "parallelSplit", "parallelJoin" ->
                    sb.append("    <parallelGateway id=\"").append(key).append("\" name=\"").append(name).append("\"/>\n");
            case "exclusiveSplit", "exclusiveJoin" ->
                    sb.append("    <exclusiveGateway id=\"").append(key).append("\" name=\"").append(name).append("\"/>\n");
            default -> throw new BizException("未知节点类型：" + n.getNodeType());
        }
        // 审批节点：总是生成终止结束事件（terminate 策略，或 back_prev 无上一用户任务时兜底）
        if ("approval".equals(n.getNodeType())) {
            sb.append("    <endEvent id=\"").append(key).append("_rejEnd\"><terminateEventDefinition/></endEvent>\n");
        }
    }

    // ------------------------------------------------------------------
    // 连线生成
    // ------------------------------------------------------------------

    private void appendFlows(StringBuilder sb, List<ProcessNode> nodes, List<ProcessLink> links,
                             Map<String, String> fieldTypes) {
        // 节点 id -> key 映射
        Map<Long, String> idToKey = nodes.stream()
                .filter(n -> n.getId() != null)
                .collect(Collectors.toMap(ProcessNode::getId, ProcessNode::getNodeKey, (a, b) -> a));

        List<ProcessLink> all = new ArrayList<>(links);
        all.sort(Comparator.comparing(l -> nullToZero(l.getSortNo())));

        for (ProcessNode node : nodes) {
            List<ProcessLink> outs = all.stream()
                    .filter(l -> idToKey.containsKey(l.getFromNodeId()))
                    .filter(l -> idToKey.get(l.getFromNodeId()).equals(node.getNodeKey()))
                    .collect(Collectors.toList());

            switch (node.getNodeType()) {
                case "approval" -> appendApprovalFlows(sb, node, outs, all, nodes, idToKey);
                case "exclusiveSplit" -> appendExclusiveFlows(sb, node, outs, idToKey, fieldTypes);
                default -> {
                    for (ProcessLink l : outs) {
                        appendFlow(sb, node.getNodeKey(), idToKey.get(l.getToNodeId()), null);
                    }
                }
            }
        }
    }

    /** 排他分叉：从节点 conditionConfig 读条件，按顺序挂到出边，默认支放最后 */
    private void appendExclusiveFlows(StringBuilder sb, ProcessNode split, List<ProcessLink> outs,
                                      Map<Long, String> idToKey, Map<String, String> fieldTypes) {
        List<ConditionDto> conds = parseConditionList(split.getConditionConfig());
        List<ProcessLink> sorted = outs.stream()
                .sorted(Comparator.comparing(l -> nullToZero(l.getSortNo())))
                .collect(Collectors.toList());
        List<ProcessLink> ordered = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ConditionDto c = i < conds.size() ? conds.get(i) : null;
            if (c == null || !Boolean.TRUE.equals(c.getIsDefault())) ordered.add(sorted.get(i));
        }
        for (int i = 0; i < sorted.size(); i++) {
            ConditionDto c = i < conds.size() ? conds.get(i) : null;
            if (c != null && Boolean.TRUE.equals(c.getIsDefault())) ordered.add(sorted.get(i));
        }
        for (ProcessLink l : ordered) {
            int idx = sorted.indexOf(l);
            ConditionDto c = idx >= 0 && idx < conds.size() ? conds.get(idx) : null;
            appendFlow(sb, split.getNodeKey(), idToKey.get(l.getToNodeId()),
                    (c == null || Boolean.TRUE.equals(c.getIsDefault())) ? null : toConditionExpression(c, fieldTypes));
        }
    }

    /** 解析排他条件配置 JSON（节点级） */
    private List<ConditionDto> parseConditionList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConditionDto>>() {});
        } catch (Exception e) {
            throw new BizException("排他条件配置解析失败：" + e.getMessage());
        }
    }

    /** 审批节点：批准线 + 驳回线（按驳回策略接目标） */
    private void appendApprovalFlows(StringBuilder sb, ProcessNode node, List<ProcessLink> outs,
                                     List<ProcessLink> allLinks, List<ProcessNode> nodes, Map<Long, String> idToKey) {
        // 批准线：正常后继
        for (ProcessLink l : outs) {
            appendFlow(sb, node.getNodeKey(), idToKey.get(l.getToNodeId()), "${approved}");
        }
        // 驳回线目标
        String rejectTarget;
        String strategy = node.getRejectStrategy() == null ? "terminate" : node.getRejectStrategy();
        if ("back_custom".equals(strategy)) {
            String target = node.getRejectTargetKey();
            rejectTarget = (target != null && !target.isBlank()) ? target : node.getNodeKey() + "_rejEnd";
        } else {
            rejectTarget = node.getNodeKey() + "_rejEnd";
        }
        appendFlow(sb, node.getNodeKey(), rejectTarget, "${!approved}");
    }


    /** 节点是否位于并行分支内（分叉与汇合之间） */
    private boolean isInParallelBranch(ProcessNode node, List<ProcessNode> nodes,
                                       List<ProcessLink> allLinks, Map<Long, String> idToKey) {
        return parallelSplitOf(node, nodes, allLinks, idToKey) != null;
    }

    /** 反向回溯遇到的第一个 parallelSplit（跨 userTask 与网关；先遇 start/多前驱返回 null） */
    private String parallelSplitOf(ProcessNode node, List<ProcessNode> nodes,
                                   List<ProcessLink> allLinks, Map<Long, String> idToKey) {
        Map<String, String> keyToType = nodes.stream()
                .collect(Collectors.toMap(ProcessNode::getNodeKey, ProcessNode::getNodeType, (a, b) -> a));
        String cur = node.getNodeKey();
        Set<String> visited = new HashSet<>();
        while (cur != null && visited.add(cur)) {
            final String curKey = cur;
            List<ProcessLink> ins = allLinks.stream()
                    .filter(l -> curKey.equals(idToKey.get(l.getToNodeId())))
                    .toList();
            if (ins.size() != 1) {
                return null; // 多前驱/无前驱，无法判断
            }
            String prevKey = idToKey.get(ins.get(0).getFromNodeId());
            String t = keyToType.getOrDefault(prevKey, "");
            if ("parallelSplit".equals(t)) {
                return prevKey; // 反向回溯遇到并行分叉 → 返回该分叉
            }
            if ("start".equals(t)) {
                return null; // 已到开始
            }
            cur = prevKey; // fill/approval/网关 → 继续回溯
        }
        return null;
    }

    /** 节点是否为「并行汇合后的第一个用户任务」（反向只跨网关，先遇 parallelJoin 则 true） */
    private boolean afterParallelJoin(ProcessNode node, List<ProcessNode> nodes,
                                      List<ProcessLink> allLinks, Map<Long, String> idToKey) {
        Map<String, String> keyToType = nodes.stream()
                .collect(Collectors.toMap(ProcessNode::getNodeKey, ProcessNode::getNodeType, (a, b) -> a));
        String cur = node.getNodeKey();
        Set<String> visited = new HashSet<>();
        while (cur != null && visited.add(cur)) {
            final String curKey = cur;
            List<ProcessLink> ins = allLinks.stream()
                    .filter(l -> curKey.equals(idToKey.get(l.getToNodeId())))
                    .toList();
            if (ins.size() != 1) {
                return false;
            }
            String prevKey = idToKey.get(ins.get(0).getFromNodeId());
            String t = keyToType.getOrDefault(prevKey, "");
            if ("parallelJoin".equals(t)) {
                return true;
            }
            if ("parallelSplit".equals(t) || "start".equals(t)
                    || "fill".equals(t) || "approval".equals(t)) {
                return false;
            }
            cur = prevKey; // 其它网关继续
        }
        return false;
    }

    /**
     * 发布前校验（与前端 wizard 约定一致，兜底）：
     * 1. 固定处理人必须指定人员；角色处理必须指定角色；
     * 2. 并行汇合后的第一个任务不能为动态处理人；
     * 3. 并行分支内 back_custom 只能回退到本分支内节点，非分支内目标不能落在并行分支内；
     * 4. back_custom 必须指定合法目标节点。
     */
    public void validateParallelRules(List<ProcessNode> nodes, List<ProcessLink> links) {
        Map<Long, String> idToKey = nodes.stream()
                .filter(n -> n.getId() != null)
                .collect(Collectors.toMap(ProcessNode::getId, ProcessNode::getNodeKey, (a, b) -> a));
        for (ProcessNode n : nodes) {
            boolean isUserTask = "fill".equals(n.getNodeType()) || "approval".equals(n.getNodeType());
            if (!isUserTask) continue;

            if ("fixed".equals(n.getAssigneeMode())
                    && (n.getFixedAssignee() == null || n.getFixedAssignee().isBlank())) {
                throw new BizException("节点「" + n.getNodeName() + "」为固定处理人，但未指定处理人");
            }
            if ("role".equals(n.getAssigneeMode())
                    && (n.getRoleCode() == null || n.getRoleCode().isBlank())) {
                throw new BizException("节点「" + n.getNodeName() + "」为角色处理，但未指定角色");
            }

            boolean afterJoin = afterParallelJoin(n, nodes, links, idToKey);

            if (afterJoin && "dynamic".equals(n.getAssigneeMode())) {
                throw new BizException("并行汇合后的第一个任务「" + n.getNodeName() + "」不能为动态处理人");
            }
            if (!"approval".equals(n.getNodeType())) continue;

            String s = n.getRejectStrategy() == null ? "terminate" : n.getRejectStrategy();
            if ("back_custom".equals(s)) {
                String target = n.getRejectTargetKey();
                if (target == null || target.isBlank()) {
                    throw new BizException("审批节点「" + n.getNodeName() + "」选择回退指定节点，但未指定目标节点");
                }
                ProcessNode t = nodeByKey(nodes, target);
                if (t == null || (!"fill".equals(t.getNodeType()) && !"approval".equals(t.getNodeType()))) {
                    throw new BizException("审批节点「" + n.getNodeName() + "」的回退目标必须是填写/审批节点");
                }
                if (!canReach(target, n.getNodeKey(), links, idToKey)) {
                    throw new BizException("审批节点「" + n.getNodeName() + "」的回退目标必须位于其之前");
                }
                String split = parallelSplitOf(n, nodes, links, idToKey);
                String targetSplit = parallelSplitOf(t, nodes, links, idToKey);
                if (split != null) {
                    if (targetSplit == null || !split.equals(targetSplit)) {
                        throw new BizException("并行分支内的审批节点「" + n.getNodeName() + "」只能回退到本分支内的节点");
                    }
                } else if (targetSplit != null) {
                    throw new BizException("审批节点「" + n.getNodeName() + "」的回退目标不能在并行分支内");
                }
            }
        }
    }

    /** 输出一条 sequenceFlow（可带条件表达式） */
    private void appendFlow(StringBuilder sb, String fromKey, String toKey, String condition) {
        sb.append("    <sequenceFlow id=\"f_").append(fromKey).append("_to_").append(toKey)
                .append("\" sourceRef=\"").append(fromKey).append("\" targetRef=\"").append(toKey).append("\"");
        if (condition == null || condition.isBlank()) {
            sb.append("/>\n");
        } else {
            sb.append(">\n      <conditionExpression xsi:type=\"tFormalExpression\">")
                    .append(xmlEscape(condition))
                    .append("</conditionExpression>\n    </sequenceFlow>\n");
        }
    }

    // ------------------------------------------------------------------
    // 条件表达式（前端可视化条件 → Flowable UEL）
    // ------------------------------------------------------------------

    private String toConditionExpression(ConditionDto c, Map<String, String> fieldTypes) {
        if (c == null || c.getFieldKey() == null || c.getFieldKey().isBlank()) {
            return null;
        }
        String op = c.getOperator() == null ? "eq" : c.getOperator();
        RuleNode left = new RuleNode.Field(c.getFieldKey());
        RuleNode right = ("isEmpty".equals(op) || "isNotEmpty".equals(op))
                ? null
                : new RuleNode.Const(c.getValue() == null ? "" : c.getValue());
        RuleNode node = new RuleNode.Compare(op, left, right);
        return "${" + RuleUelCompiler.compile(node, fieldTypes) + "}";
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private ProcessNode nodeByKey(List<ProcessNode> nodes, String key) {
        return nodes.stream().filter(n -> key.equals(n.getNodeKey())).findFirst().orElse(null);
    }

    /** 判断 fromKey 是否能沿连线到达 toKey */
    private boolean canReach(String fromKey, String toKey, List<ProcessLink> links, Map<Long, String> idToKey) {
        if (fromKey.equals(toKey)) return false;
        Map<String, List<String>> out = new HashMap<>();
        for (ProcessLink l : links) {
            String f = idToKey.get(l.getFromNodeId());
            String t = idToKey.get(l.getToNodeId());
            if (f == null || t == null) continue;
            out.computeIfAbsent(f, k -> new ArrayList<>()).add(t);
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(fromKey);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!visited.add(cur)) continue;
            if (cur.equals(toKey)) return true;
            for (String nx : out.getOrDefault(cur, List.of())) queue.add(nx);
        }
        return false;
    }

    private static int nullToZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}