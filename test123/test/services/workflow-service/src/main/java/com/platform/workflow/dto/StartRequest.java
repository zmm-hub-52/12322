package com.platform.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/** 发起流程请求（v2：引擎驱动；assignees 指定各任务节点处理人） */
@Data
public class StartRequest {

    /** 流程定义ID */
    @NotNull(message = "processDefId 不能为空")
    private Long processDefId;

    /** 业务编号 */
    private String bizNo;

    /** 实例标题 */
    private String title;

    /** 发起人（同时作为填写节点处理人） */
    @NotBlank(message = "applicant 不能为空")
    private String applicant;

    /** 发起表单数据（字段值，将同时作为流程变量供条件路由） */
    private Map<String, Object> formData;

    /** 各任务节点处理人：nodeKey -> 处理人（fill 节点默认用发起人，可覆盖） */
    private Map<String, String> assignees;
}
