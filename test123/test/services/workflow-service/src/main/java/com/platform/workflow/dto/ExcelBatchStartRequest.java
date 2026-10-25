package com.platform.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Excel 明细批量发起请求：多行数据作为一个流程实例的明细表发起。 */
@Data
public class ExcelBatchStartRequest {

    /** 流程定义ID */
    private Long processDefId;

    /** 明细行数据（key -> 值；后端按明细列字段类型转换） */
    private List<Map<String, Object>> rows;

    /** 发起人（当前登录用户 username） */
    private String applicant;

    /** 审批人：nodeKey -> username */
    private Map<String, String> assignees;

    /** 业务单号（可空，后端自动生成） */
    private String bizNo;

    /** 标题（可空，默认流程名+序号） */
    private String title;
}
