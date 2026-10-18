package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ⑤ 流程实例（v2：业务单据与引擎实例的映射）。
 * 状态/任务完全由 Flowable 提供（ACT_HI_PROCINST / ACT_RU_TASK），
 * 这里只保留业务单据信息 + flowable_instance_id 映射 + 表单数据。
 * 运行数据，不软删除（实例历史需保留）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_instance")
public class ProcessInstance extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程定义ID */
    private Long processDefId;

    /** 所用流程定义版本（标识实例跑的是哪版流程） */
    private Integer processDefVersion;

    /** 引用 form_schema_snapshot 表（运行期只读快照，同版本实例共享一份） */
    private Long formSchemaSnapshotId;

    /** Flowable 引擎实例 ID（引擎驱动映射；businessKey 亦为本表 id 字符串） */
    private String flowableInstanceId;

    /** 业务编号 */
    private String bizNo;

    /** 实例标题 */
    private String title;

    /** 发起人 */
    private String applicant;

    /** JSON：业务数据（所有动态字段的值，全流程共享一份；亦作为流程变量供条件路由） */
    private String formData;
}
