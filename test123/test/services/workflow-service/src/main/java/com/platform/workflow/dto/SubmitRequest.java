package com.platform.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/** 填写节点提交请求（v2：taskId 为 Flowable 引擎任务 ID） */
@Data
public class SubmitRequest {

    /** 引擎任务ID */
    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    /** 本节点提交的表单数据（合并进实例数据并作为流程变量） */
    private Map<String, Object> formData;

    /** 下一处理人（nodeKey -> 处理人；assignee_mode=dynamic 的下一用户任务需指定） */
    private Map<String, String> nextAssignees;
}
