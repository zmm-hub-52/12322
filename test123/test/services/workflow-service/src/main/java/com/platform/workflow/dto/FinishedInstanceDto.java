package com.platform.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 已完结流程实例视图（列表 + 导出入口） */
@Data
public class FinishedInstanceDto {

    /** 业务实例 ID */
    private Long id;

    /** 业务编号 */
    private String bizNo;

    /** 实例标题 */
    private String title;

    /** 发起人 */
    private String applicant;

    /** 流程名称 */
    private String processName;

    /** 发起时间 */
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：finished 正常走完 / terminated 驳回终止 */
    private String status;
}
