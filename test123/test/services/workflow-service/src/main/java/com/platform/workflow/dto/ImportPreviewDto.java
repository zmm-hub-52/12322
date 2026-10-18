package com.platform.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Excel 导入预览结果（两步交互：上传 → 预览校验 → 逐行发起） */
@Data
public class ImportPreviewDto {

    /** 模板列（= start 字段 key，按列顺序） */
    private List<String> keys;

    /** 行数据与校验结果 */
    private List<ImportRowDto> rows;
}
