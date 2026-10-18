package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ⑧ 维度（制造业主数据维度：物料 / 质量 / 采购 ...）。
 * 字段可选值的「维度」层级，取代扁平的字典表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dimension")
public class Dimension extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维度编码：material / quality / purchase */
    private String dimensionCode;

    /** 维度名称：物料 / 质量 / 采购 */
    private String dimensionName;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
