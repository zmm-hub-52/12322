package com.platform.workflow.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.entity.NodeFormField;
import com.platform.workflow.mapper.NodeFormFieldMapper;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuleExcelService {

    private final NodeFormFieldMapper nodeFormFieldMapper;
    private final SchemaAssembler schemaAssembler;
    private final ObjectMapper objectMapper;

    private static final List<String> BASIC_HEADERS = List.of(
            "fieldKey", "fieldName", "ruleType", "options", "prefix", "start", "end", "width", "min", "max", "pattern");
    private static final List<String> REQUIRED_HEADERS = List.of(
            "fieldKey", "fieldName", "triggerField", "operator", "triggerValue", "innerType", "innerOptions",
            "innerPrefix", "innerStart", "innerEnd", "innerWidth", "innerMin", "innerMax", "innerPattern");
    private static final List<String> VALUE_HEADERS = List.of(
            "fieldKey", "fieldName", "caseField", "operator", "caseValue", "expect");

    public byte[] exportRules(Long nodeId) {
        List<Map<String, Object>> fields = schemaAssembler.assembleNodeFields(nodeId);
        List<List<Object>> basicRows = new ArrayList<>();
        List<List<Object>> requiredRows = new ArrayList<>();
        List<List<Object>> valueRows = new ArrayList<>();

        for (Map<String, Object> f : fields) {
            String key = str(f.get("key"));
            String name = str(f.get("label"));
            Map<String, Object> rule = map(f.get("rule"));
            String type = rule == null ? "" : str(rule.get("type"));
            // 基础规则 Sheet 导出所有已选字段；无规则也保留一行，ruleType 为空
            basicRows.add(List.of(key, name, type,
                    rule == null ? "" : str(rule.get("options")),
                    rule == null ? "" : str(rule.get("prefix")),
                    rule == null ? "" : str(rule.get("start")),
                    rule == null ? "" : str(rule.get("end")),
                    rule == null ? "" : str(rule.get("width")),
                    rule == null ? "" : str(rule.get("min")),
                    rule == null ? "" : str(rule.get("max")),
                    rule == null ? "" : str(rule.get("pattern"))));
            if (rule == null) continue;
            if ("requiredWhen".equals(type)) {
                Map<String, Object> inner = map(rule.get("rule"));
                requiredRows.add(List.of(key, name, str(rule.get("fieldKey")), str(rule.get("operator")), str(rule.get("value")),
                        inner == null ? "" : str(inner.get("type")),
                        inner == null ? "" : str(inner.get("options")),
                        inner == null ? "" : str(inner.get("prefix")),
                        inner == null ? "" : str(inner.get("start")),
                        inner == null ? "" : str(inner.get("end")),
                        inner == null ? "" : str(inner.get("width")),
                        inner == null ? "" : str(inner.get("min")),
                        inner == null ? "" : str(inner.get("max")),
                        inner == null ? "" : str(inner.get("pattern"))));
            } else if ("valueWhen".equals(type)) {
                Object casesObj = rule.get("cases");
                if (casesObj instanceof List<?> cases) {
                    for (Object c : cases) {
                        Map<String, Object> cm = map(c);
                        valueRows.add(List.of(key, name, str(cm.get("fieldKey")), str(cm.get("operator")), str(cm.get("value")), str(cm.get("expect"))));
                    }
                }
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (ExcelWriter writer = EasyExcel.write(out).registerWriteHandler(excelStyle()).build()) {
                writer.write(basicRows, EasyExcel.writerSheet("基础规则").head(toHead(BASIC_HEADERS)).build());
                writer.write(requiredRows, EasyExcel.writerSheet("requiredWhen").head(toHead(REQUIRED_HEADERS)).build());
                writer.write(valueRows, EasyExcel.writerSheet("valueWhen").head(toHead(VALUE_HEADERS)).build());
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("规则导出失败：" + e.getMessage());
        }
    }

    public void importRules(Long nodeId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请上传规则 Excel");
        List<NodeFormField> existing = nodeFormFieldMapper.selectList(
                new LambdaQueryWrapper<NodeFormField>().eq(NodeFormField::getNodeId, nodeId));
        Map<String, NodeFormField> byKey = new LinkedHashMap<>();
        for (NodeFormField f : existing) byKey.put(f.getFieldKey(), f);

        for (Map<String, String> row : readSheet(file, 0, BASIC_HEADERS)) {
            String key = row.get("fieldKey");
            if (key == null || key.isBlank()) continue;
            String type = row.getOrDefault("ruleType", "");
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", type);
            if ("enum".equals(type)) rule.put("options", split(row.getOrDefault("options", "")));
            if ("codeRange".equals(type)) {
                rule.put("prefix", row.getOrDefault("prefix", ""));
                rule.put("start", intOrNull(row.get("start")));
                rule.put("end", intOrNull(row.get("end")));
                rule.put("width", intOrNull(row.get("width")));
            }
            if ("range".equals(type)) {
                rule.put("min", dblOrNull(row.get("min")));
                rule.put("max", dblOrNull(row.get("max")));
            }
            if ("pattern".equals(type)) rule.put("pattern", row.getOrDefault("pattern", ""));
            updateOverride(byKey.get(key), nodeId, key, rule);
        }

        for (Map<String, String> row : readSheet(file, 1, REQUIRED_HEADERS)) {
            String key = row.get("fieldKey");
            if (key == null || key.isBlank()) continue;
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "requiredWhen");
            rule.put("fieldKey", row.getOrDefault("triggerField", ""));
            rule.put("operator", row.getOrDefault("operator", "eq"));
            rule.put("value", row.getOrDefault("triggerValue", ""));
            String innerType = row.getOrDefault("innerType", "");
            if (innerType != null && !innerType.isBlank()) {
                Map<String, Object> inner = new LinkedHashMap<>();
                inner.put("type", innerType);
                if ("enum".equals(innerType)) inner.put("options", split(row.getOrDefault("innerOptions", "")));
                if ("codeRange".equals(innerType)) {
                    inner.put("prefix", row.getOrDefault("innerPrefix", ""));
                    inner.put("start", intOrNull(row.get("innerStart")));
                    inner.put("end", intOrNull(row.get("innerEnd")));
                    inner.put("width", intOrNull(row.get("innerWidth")));
                }
                if ("range".equals(innerType)) {
                    inner.put("min", dblOrNull(row.get("innerMin")));
                    inner.put("max", dblOrNull(row.get("innerMax")));
                }
                if ("pattern".equals(innerType)) inner.put("pattern", row.getOrDefault("innerPattern", ""));
                rule.put("rule", inner);
            }
            updateOverride(byKey.get(key), nodeId, key, rule);
        }

        Map<String, List<Map<String, Object>>> valueByField = new LinkedHashMap<>();
        for (Map<String, String> row : readSheet(file, 2, VALUE_HEADERS)) {
            String key = row.get("fieldKey");
            if (key == null || key.isBlank()) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("fieldKey", row.getOrDefault("caseField", ""));
            c.put("operator", row.getOrDefault("operator", "eq"));
            c.put("value", row.getOrDefault("caseValue", ""));
            c.put("expect", row.getOrDefault("expect", ""));
            valueByField.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : valueByField.entrySet()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "valueWhen");
            rule.put("cases", e.getValue());
            updateOverride(byKey.get(e.getKey()), nodeId, e.getKey(), rule);
        }
    }

    private HorizontalCellStyleStrategy excelStyle() {
        WriteCellStyle head = new WriteCellStyle();
        head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 12);
        head.setWriteFont(headFont);
        head.setBorderBottom(BorderStyle.THIN);
        head.setBorderTop(BorderStyle.THIN);
        head.setBorderLeft(BorderStyle.THIN);
        head.setBorderRight(BorderStyle.THIN);

        WriteCellStyle content = new WriteCellStyle();
        content.setBorderBottom(BorderStyle.THIN);
        content.setBorderTop(BorderStyle.THIN);
        content.setBorderLeft(BorderStyle.THIN);
        content.setBorderRight(BorderStyle.THIN);
        return new HorizontalCellStyleStrategy(head, content);
    }

    private List<List<String>> toHead(List<String> headers) {
        List<List<String>> head = new ArrayList<>();
        for (String h : headers) head.add(List.of(h));
        return head;
    }

    private List<Map<String, String>> readSheet(MultipartFile file, int sheetIndex, List<String> headers) {
        try (InputStream in = file.getInputStream()) {
            List<Map<Integer, String>> raw = EasyExcel.read(in).sheet(sheetIndex).headRowNumber(1).doReadSync();
            List<Map<String, String>> rows = new ArrayList<>();
            if (raw == null) return rows;
            for (Map<Integer, String> r : raw) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), r.getOrDefault(i, "").trim());
                }
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            throw new BizException("规则 Excel 解析失败：" + e.getMessage());
        }
    }

    private void updateOverride(NodeFormField nff, Long nodeId, String fieldKey, Map<String, Object> rule) {
        if (nff == null) {
            nff = new NodeFormField();
            nff.setNodeId(nodeId);
            nff.setFieldKey(fieldKey);
            nff.setMode("editable");
            nff.setOrderNo(0);
        }
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("rule", rule);
        try {
            nff.setOverride(objectMapper.writeValueAsString(override));
        } catch (Exception e) {
            throw new BizException("规则序列化失败：" + e.getMessage());
        }
        if (nff.getId() == null) {
            nodeFormFieldMapper.insert(nff);
        } else {
            nodeFormFieldMapper.updateById(nff);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object v) { return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null; }
    private String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private List<String> split(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> list = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isBlank()) list.add(p.trim());
        return list;
    }
    private Integer intOrNull(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; } }
    private Double dblOrNull(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; } }
}