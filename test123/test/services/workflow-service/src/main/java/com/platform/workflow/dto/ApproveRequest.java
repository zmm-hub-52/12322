package com.platform.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
