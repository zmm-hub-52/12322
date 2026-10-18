package com.platform.workflow.dto;

import lombok.Data;

import java.util.Map;

/** Excel 导入逐行发起请求（预览通过的行，前端点「发起」逐条提交） */
@Data
public class ExcelStartRequest {

    /** 流程定义ID */
    private Long processDefId;

    /** 该行数据（key -> 值；后端按 start 字段类型转换） */
    private Map<String, Object> data;

    /** 发起人（当前登录用户 username） */
    private String applicant;

    /** 审批人：nodeKey -> username */
    private Map<String, String> assignees;

    /** 业务单号（可空，后端自动生成） */
    private String bizNo;

    /** 标题（可空，默认流程名+序号） */
    private String title;
}
