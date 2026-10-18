package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ⑥ 流程轨迹（v2 新增：每一步操作留痕）。
 * 执行人/动作/审批意见为业务数据，自持存储，不依赖 Flowable 的 comment 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_record")
public class ProcessRecord extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务实例 */
    private Long processInstanceId;

    /** 步骤节点（= BPMN taskId） */
    private String nodeKey;

    /** 节点名称（记录时冗余） */
    private String nodeName;

    /** 动作：start / submit / approve / reject / terminate */
    private String actionType;

    /** 执行人 */
    private String executor;

    /** 审批意见/备注（业务数据） */
    private String comment;

    /** 冗余引擎任务 id（溯源） */
    private String flowableTaskId;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
