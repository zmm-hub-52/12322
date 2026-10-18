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

            // 必填
            if (Boolean.TRUE.equals(f.get("required")) && empty) {
                errors.add(label + "（" + key + "）为必填");
                continue;
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

            // 取值规则校验
            @SuppressWarnings("unchecked")
            Map<String, Object> valueRule = (Map<String, Object>) f.get("valueRule");
            if (valueRule != null) {
                String ruleType = String.valueOf(valueRule.getOrDefault("type", ""));
                if ("enum".equals(ruleType)) {
                    @SuppressWarnings("unchecked")
                    List<String> options = (List<String>) valueRule.getOrDefault("options", List.of());
                    if (!options.contains(value)) {
                        errors.add(label + " 枚举值不合法，可选：" + String.join("/", options));
                    }
                } else if ("range".equals(ruleType)) {
                    try {
                        double num = Double.parseDouble(value);
                        Object minO = valueRule.get("min");
                        Object maxO = valueRule.get("max");
                        if (minO instanceof Number && num < ((Number) minO).doubleValue()) {
                            errors.add(label + " 不能小于 " + minO);
                        }
                        if (maxO instanceof Number && num > ((Number) maxO).doubleValue()) {
                            errors.add(label + " 不能大于 " + maxO);
                        }
                    } catch (NumberFormatException e) {
                        // 非数字已由上方 number 类型校验报错，此处去重
                    }
                }
            }

            // 正则校验
            @SuppressWarnings("unchecked")
            Map<String, Object> validation = (Map<String, Object>) f.get("validation");
            if (validation != null && validation.get("pattern") != null) {
                try {
                    if (!Pattern.compile(String.valueOf(validation.get("pattern"))).matcher(value).matches()) {
                        errors.add(label + " 格式不正确");
                    }
                } catch (Exception ignored) {
                    /* 非法正则忽略 */
                }
            }
        }
        return errors;
    }
}
