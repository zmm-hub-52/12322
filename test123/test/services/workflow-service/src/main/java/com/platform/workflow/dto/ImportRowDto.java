package com.platform.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Excel 导入单行（预览） */
@Data
public class ImportRowDto {

    /** Excel 行号（从 1 开始，1 为表头） */
    private Integer rowNo;

    /** 字段值（key -> 原始字符串值） */
    private Map<String, String> data;

    /** 校验是否通过 */
    private boolean valid;

    /** 校验错误列表（valid=false 时非空） */
    private List<String> errors;
}
