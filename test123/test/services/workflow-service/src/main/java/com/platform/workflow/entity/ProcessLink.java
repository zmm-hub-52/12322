package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ③ 节点连线（v2 新增：DAG 边）。
 * 排他分叉出边携带条件（condition_json），同源出边按 sort_no 决定条件匹配优先级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_link")
public class ProcessLink extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程定义 */
    private Long processDefId;

    /** 源节点 */
    private Long fromNodeId;

    /** 目标节点 */
    private Long toNodeId;

    /** 排他分支条件 JSON：{ fieldKey, operator, value, isDefault } */
    private String conditionJson;

    /** 同源出边顺序（条件匹配优先级） */
    private Integer sortNo;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
