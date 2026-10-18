package com.platform.workflow.dto;

import lombok.Data;

import java.util.Date;

/** 待办任务视图（引擎任务 + 业务实例上下文） */
@Data
public class TaskViewDto {

    /** 引擎任务 ID */
    private String taskId;

    /** 业务实例 ID（= businessKey） */
    private Long instanceId;

    /** 业务编号 */
    private String bizNo;

    /** 实例标题 */
    private String title;

    /** 任务定义 key（= nodeKey = 表单快照 key） */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型（fill/approval） */
    private String nodeType;

    /** 任务创建时间 */
    private Date createTime;
}
