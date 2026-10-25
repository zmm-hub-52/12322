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
 * 职责：把「配置表」（node_form_field 引用 field_def）拼装成「自包含的完整 schema JSON」。
 * 纯表格模型：一个节点 = 一张表 = 列数组（每个 NodeFormField 是一列）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaAssembler {

    private final ProcessNodeMapper processNodeMapper;
    private final NodeFormFieldMapper nodeFormFieldMapper;
    private final FieldDefMapper fieldDefMapper;
    private final ObjectMapper objectMapper;

    private static final String SCHEMA_VERSION = "1.0";

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
                log.warn("[schema] 字段库不存在 key={}，跳过", nff.getFieldKey());
                continue;
            }
            fields.add(assembleField(def, nff));
        }
        return fields;
    }

    private Map<String, Object> assembleField(FieldDef def, NodeFormField nff) {
        Map<String, Object> override = parseJsonObject(nff.getOverride());

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", def.getFieldKey());
        field.put("label", def.getFieldName());
        field.put("type", def.getFieldType());
        field.put("mode", nff.getMode());
        field.put("required", override != null && override.containsKey("required")
                ? override.get("required") : Boolean.TRUE.equals(def.getRequired()));
        field.put("rule", override != null && override.get("rule") != null
                ? override.get("rule") : parseJsonObject(def.getFieldRule()));
        field.put("defaultValue", override != null && override.containsKey("defaultValue")
                ? override.get("defaultValue") : def.getDefaultValue());
        field.put("description", def.getDescription());
        field.put("placeholder", null);
        return field;
    }

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
