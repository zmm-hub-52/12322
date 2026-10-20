package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ③ 节点字段配置（= schema 实体）。
 * 每个审批节点的表单由这些行组成：引用字段库的 field_key + 节点级配置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("node_form_field")
public class NodeFormField extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属节点 */
    private Long nodeId;

    /** 引用 field_def.field_key（字段库），不是存字段全量 */
    private String fieldKey;

    /** 字段显示顺序 */
    private Integer orderNo;

    /**
     * 节点级状态：
     * editable 可编辑 / readonly 只读审批 / hidden 隐藏。
     * 用于多级审批时同一字段在不同节点的可见性与编辑性。
     */
    private String mode;

    /**
     * 节点级覆盖 JSON：{ "required": bool, "rule": {...} }
     * 可空；空则全部继承字段库默认。用于「同字段不同流程约束不同」的场景。
     */
    private String override;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
