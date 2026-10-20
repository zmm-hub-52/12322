package com.platform.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/** 审批任务请求（v2：approved 变量驱动 BPMN 批准/驳回线） */
@Data
public class ApproveRequest {

    /** 引擎任务ID */
    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    /** 审批意见（写入业务轨迹表 process_record） */
    private String comment;

    /** 是否通过（true 走批准线；false 走驳回线，按节点驳回策略路由） */
    private boolean approved;

    /** 审批节点可编辑字段的表单数据（合并进实例数据） */
    private Map<String, Object> formData;

    /** 下一处理人（nodeKey -> 处理人；批准线下一动态用户任务需指定） */
    private Map<String, String> nextAssignees;
}
