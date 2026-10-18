package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ⑨ 维度分类（维度下的子分类）。
 * 如物料维度下的 ROH 原材料 / HALB 半成品 / FERT 成品。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dimension_category")
public class DimensionCategory extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属维度 */
    private Long dimensionId;

    /** 分类编码：ROH / HALB / FERT */
    private String categoryCode;

    /** 分类名称：原材料 / 半成品 / 成品 */
    private String categoryName;

    /** 排序 */
    private Integer sortNo;

    /** 软删除 */
    @TableLogic
    private Boolean enabled;
}
