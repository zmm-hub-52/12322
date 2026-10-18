package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.entity.FieldDef;
import com.platform.workflow.entity.NodeFormField;
import com.platform.workflow.entity.ProcessNode;
import com.platform.workflow.mapper.FieldDefMapper;
import com.platform.workflow.mapper.NodeFormFieldMapper;
import com.platform.workflow.mapper.ProcessNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema 拼装器（核心）。
 *
 * 职责：把「配置表」（node_form_field 引用 field_def）拼装成「自包含的完整 schema JSON」，
 * 返回给前端渲染器直接消费（前端不自己 join）。
 *
 * 拼装发生在两个时机：
 * 1. 流程发布时 → 生成全流程快照，存 form_schema_snapshot 表（此后运行期只读快照）。
 * 2. 配置期预览时 → 实时拼装，供管理员预览表单效果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaAssembler {

    private final ProcessNodeMapper processNodeMapper;
    private final NodeFormFieldMapper nodeFormFieldMapper;
    private final FieldDefMapper fieldDefMapper;
    private final ObjectMapper objectMapper;

    /** schema 协议版本（结构演进时递增，前端渲染器按版本兼容解析） */
    private static final String SCHEMA_VERSION = "1.0";

    /**
     * 拼装某流程「所有节点」的完整 schema。
     * 返回结构：{ schemaVersion, nodes: { nodeKey: [fields...], ... } }
     *
     * @param processDefId 流程定义ID
     * @return schema JSON 字符串
     */
    public String assembleFullSchema(Long processDefId) {
        List<ProcessNode> nodes = processNodeMapper.selectList(
                new LambdaQueryWrapper<ProcessNode>()
                        .eq(ProcessNode::getProcessDefId, processDefId)
                        .orderByAsc(ProcessNode::getId));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        Map<String, Object> nodesMap = new LinkedHashMap<>();
        for (ProcessNode node : nodes) {
            nodesMap.put(node.getNodeKey(), assembleNodeFields(node.getId()));
        }
        root.put("nodes", nodesMap);
        return toJson(root);
    }

    /**
     * 拼装单个节点的表单字段列表。
     * 每个字段是「字段库默认 + 节点级覆盖」合并后的完整定义。
     *
     * @param nodeId 节点ID
     * @return 字段定义列表
     */
    public List<Map<String, Object>> assembleNodeFields(Long nodeId) {
        List<NodeFormField> nodeFields = nodeFormFieldMapper.selectList(
                new LambdaQueryWrapper<NodeFormField>()
                        .eq(NodeFormField::getNodeId, nodeId)
                        .orderByAsc(NodeFormField::getOrderNo));

        List<Map<String, Object>> fields = new ArrayList<>();
        for (NodeFormField nff : nodeFields) {
            FieldDef def = fieldDefMapper.selectOne(
                    new LambdaQueryWrapper<FieldDef>().eq(FieldDef::getFieldKey, nff.getFieldKey()));
            if (def == null) {
                // 字段库软删除不会导致引用失效；此处防御性跳过
                log.warn("[schema] 字段库不存在 key={}，跳过", nff.getFieldKey());
                continue;
            }
            fields.add(assembleField(def, nff));
        }
        return fields;
    }

    /**
     * 拼装单个字段的完整定义：字段库默认 + 节点级 override 覆盖。
     * 字段结构（与前端渲染器约定）：
     * { key, label, type, mode, required, valueRule, validation, defaultValue, placeholder, rule }
     */
    private Map<String, Object> assembleField(FieldDef def, NodeFormField nff) {
        Map<String, Object> override = parseJsonObject(nff.getOverride());

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", def.getFieldKey());
        field.put("label", def.getFieldName());
        field.put("type", def.getFieldType());
        field.put("mode", nff.getMode());
        // 节点级覆盖优先，否则用字段库默认
        field.put("required", override != null && override.containsKey("required")
                ? override.get("required") : Boolean.TRUE.equals(def.getRequired()));
        field.put("valueRule", override != null && override.get("valueRule") != null
                ? override.get("valueRule") : parseJsonObject(def.getValueRule()));
        field.put("validation", override != null && override.get("validation") != null
                ? override.get("validation") : parseJsonObject(def.getValidation()));
        // 以下为预留槽位（第一版不实现）
        field.put("defaultValue", null);
        field.put("placeholder", null);
        field.put("rule", null);
        return field;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("JSON 解析失败：" + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败：" + e.getMessage());
        }
    }
}
