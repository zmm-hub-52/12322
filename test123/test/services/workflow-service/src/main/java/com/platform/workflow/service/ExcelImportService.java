package com.platform.workflow.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.dto.ImportPreviewDto;
import com.platform.workflow.dto.ImportRowDto;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.entity.ProcessNode;
import com.platform.workflow.mapper.ProcessDefMapper;
import com.platform.workflow.mapper.ProcessNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Excel 导入发起服务（EasyExcel 动态表头）。
 * - 模板列 = start 节点配置的字段（表头一行 = 字段 key）；
 * - 上传解析：无模型读（每行 Map<Integer,String>），按列顺序映射 key；
 * - 校验：必填 / 数字 / 日期 / 枚举选项 / 数值范围 / 正则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ProcessNodeMapper nodeMapper;
    private final ProcessDefMapper defMapper;
    private final SchemaAssembler schemaAssembler;

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** start 节点字段定义（模板列 + 校验规则来源） */
    public List<Map<String, Object>> getStartFields(Long defId) {
        ProcessNode start = nodeMapper.selectOne(
                new LambdaQueryWrapper<ProcessNode>()
                        .eq(ProcessNode::getProcessDefId, defId)
                        .eq(ProcessNode::getNodeType, "start"));
        if (start == null) {
            return List.of();
        }
        return schemaAssembler.assembleNodeFields(start.getId());
    }

    /** 生成模板字节（表头 = 字段 key 一行） */
    public byte[] buildTemplate(Long defId) {
        List<Map<String, Object>> fields = getStartFields(defId);
        if (fields.isEmpty()) {
            throw new BizException("该流程未配置发起字段（请在表单配置页给「开始」节点配置字段）");
        }
        List<List<String>> head = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            head.add(List.of(String.valueOf(f.get("key"))));
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("data").doWrite(List.of());
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("模板生成失败：" + e.getMessage());
        }
    }

    /** 解析 + 校验（不发起；前端预览后逐行点发起） */
    public ImportPreviewDto parseAndValidate(Long defId, MultipartFile file) {
        List<Map<String, Object>> fields = getStartFields(defId);
        if (fields.isEmpty()) {
            throw new BizException("该流程未配置发起字段（请在表单配置页给「开始」节点配置字段）");
        }
        List<String> keys = fields.stream().map(f -> String.valueOf(f.get("key"))).toList();

        // 无模型读：每行 Map<列索引, 值>
        List<Map<Integer, String>> rawRows;
        try (InputStream in = file.getInputStream()) {
            rawRows = EasyExcel.read(in).sheet(0).headRowNumber(1).doReadSync();
        } catch (Exception e) {
            throw new BizException("Excel 解析失败：" + e.getMessage());
        }

        ImportPreviewDto dto = new ImportPreviewDto();
        dto.setKeys(keys);
        List<ImportRowDto> rows = new ArrayList<>();
        if (rawRows == null) {
            dto.setRows(rows);
            return dto;
        }
        int rowNo = 2; // 第 1 行为表头
        for (Map<Integer, String> raw : rawRows) {
            Map<String, String> data = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                String v = raw.get(i);
                data.put(keys.get(i), v == null ? "" : v.trim());
            }
            List<String> errors = validateRow(fields, data);
            ImportRowDto row = new ImportRowDto();
            row.setRowNo(rowNo);
            row.setData(data);
            row.setErrors(errors);
            row.setValid(errors.isEmpty());
            rows.add(row);
            rowNo++;
        }
        dto.setRows(rows);
        return dto;
    }

    /** 单行校验（按 start 字段规则） */
    private List<String> validateRow(List<Map<String, Object>> fields, Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            String key = String.valueOf(f.get("key"));
            String label = String.valueOf(f.get("label"));
            String type = String.valueOf(f.get("type"));
            String value = data.getOrDefault(key, "");
            boolean empty = value.isEmpty();

            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) f.get("rule");
            String ruleType = rule == null ? "" : String.valueOf(rule.getOrDefault("type", ""));

            // 必填（含条件必填 requiredWhen）
            boolean required = Boolean.TRUE.equals(f.get("required"));
            boolean condRequired = "requiredWhen".equals(ruleType) && matchCondition(rule, data);
            if ((required || condRequired) && empty) {
                errors.add(label + "（" + key + "）为必填");
                continue;
            }

            // 条件取值校验（valueWhen）：命中第一条满足条件的 case 时值必须等于 expect
            if ("valueWhen".equals(ruleType)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cases = (List<Map<String, Object>>) rule.getOrDefault("cases", List.of());
                for (Map<String, Object> c : cases) {
                    if (matchCondition(c, data)) {
                        String expect = String.valueOf(c.getOrDefault("expect", ""));
                        if (!expect.equals(value)) {
                            errors.add(label + " 当前应为 " + expect);
                        }
                        break; // 命中第一条即生效
                    }
                }
            }

            if (empty) continue;

            // 类型校验
            if ("number".equals(type)) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    errors.add(label + " 需为数字，当前：" + value);
                }
            } else if ("date".equals(type) && !DATE_PATTERN.matcher(value).matches()) {
                errors.add(label + " 需为 YYYY-MM-DD 格式，当前：" + value);
            }

            // 规则校验（取值+校验二合一）：enum / codeRange / range / pattern
            if (rule != null) {
                applyValueRule(rule, value, label, errors);
                // 条件必填附带的内层取值约束（命中条件时）
                if ("requiredWhen".equals(ruleType) && condRequired) {
                    Object inner = rule.get("rule");
                    if (inner instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> innerRule = (Map<String, Object>) inner;
                        applyValueRule(innerRule, value, label, errors);
                    }
                }
            }
        }
        return errors;
    }

    /** 对单个取值约束（enum/codeRange/range/pattern）校验，命中则追加错误 */
    private void applyValueRule(Map<String, Object> rule, String value, String label, List<String> errors) {
        String rt = String.valueOf(rule.getOrDefault("type", ""));
        if ("enum".equals(rt)) {
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) rule.getOrDefault("options", List.of());
            if (!options.contains(value)) {
                errors.add(label + " 枚举值不合法，可选：" + String.join("/", options));
            }
        } else if ("range".equals(rt)) {
            try {
                double num = Double.parseDouble(value);
                Object minO = rule.get("min");
                Object maxO = rule.get("max");
                if (minO instanceof Number && num < ((Number) minO).doubleValue()) {
                    errors.add(label + " 不能小于 " + minO);
                }
                if (maxO instanceof Number && num > ((Number) maxO).doubleValue()) {
                    errors.add(label + " 不能大于 " + maxO);
                }
            } catch (NumberFormatException e) {
                // 非数字已由上方 number 类型校验报错
            }
        } else if ("codeRange".equals(rt)) {
            String prefix = String.valueOf(rule.getOrDefault("prefix", ""));
            if (!prefix.isEmpty() && !value.startsWith(prefix)) {
                errors.add(label + " 编码需以 " + prefix + " 开头");
            } else {
                try {
                    int n = Integer.parseInt(value.substring(prefix.length()));
                    int start = rule.get("start") instanceof Number ? ((Number) rule.get("start")).intValue() : 1;
                    int end = rule.get("end") instanceof Number ? ((Number) rule.get("end")).intValue() : 999;
                    if (n < start || n > end) {
                        errors.add(label + " 编码序号需在 " + start + "~" + end + " 之间");
                    }
                } catch (NumberFormatException e) {
                    errors.add(label + " 编码格式不正确");
                }
            }
        } else if ("pattern".equals(rt)) {
            Object pattern = rule.get("pattern");
            if (pattern != null) {
                try {
                    if (!Pattern.compile(String.valueOf(pattern)).matcher(value).matches()) {
                        errors.add(label + " 格式不正确");
                    }
                } catch (Exception ignored) {
                    /* 非法正则忽略 */
                }
            }
        }
    }

    /** 判断某条件（fieldKey/operator/value）在当前行数据下是否成立 */
    private boolean matchCondition(Map<String, Object> cond, Map<String, String> data) {
        String fieldKey = String.valueOf(cond.getOrDefault("fieldKey", ""));
        String op = String.valueOf(cond.getOrDefault("operator", "eq"));
        String cv = String.valueOf(cond.getOrDefault("value", ""));
        String tv = data.getOrDefault(fieldKey, "");
        return switch (op) {
            case "isEmpty" -> tv.isEmpty();
            case "isNotEmpty" -> !tv.isEmpty();
            case "contains" -> tv.contains(cv);
            case "neq" -> !tv.equals(cv);
            case "gt" -> toDouble(tv) > toDouble(cv);
            case "gte" -> toDouble(tv) >= toDouble(cv);
            case "lt" -> toDouble(tv) < toDouble(cv);
            case "lte" -> toDouble(tv) <= toDouble(cv);
            default -> tv.equals(cv);
        };
    }

    private double toDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
