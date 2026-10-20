package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ② 流程节点（v2：node_key 即 BPMN taskId，画布节点 / 引擎任务 / 表单快照 三合一 key）。
 * 节点的表单字段不在这张表，见 {@link NodeFormField}；流转关系由 {@link ProcessLink} 表达。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_node")
public class ProcessNode extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程定义 */
    private Long processDefId;

    /** 节点标识（= BPMN userTask/gateway id，表单快照 key） */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /**
     * 节点类型：
     * start 发起 / fill 填写 / approval 审批 /
     * parallelSplit 并行分叉 / parallelJoin 并行汇合 /
     * exclusiveSplit 排他分叉 / exclusiveJoin 排他汇合 / end 结束。
     */
    private String nodeType;

    /** 审批节点驳回策略：back_prev 退回上一步 / back_starter 驳回到发起人 / terminate 终止流程 */
    private String rejectStrategy;

    /** 处理人模式：fixed 固定（配置期指定）/ dynamic 动态（上一环节选下一人） */
    private String assigneeMode;

    /** 固定处理人（assignee_mode=fixed 时生效，存 username） */
    private String fixedAssignee;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
