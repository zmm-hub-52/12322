package com.platform.workflow.dto;

import lombok.Data;

/** 图连线 DTO（前后端以 nodeKey 交互，落库时转换为节点 id） */
@Data
public class LinkDto {

    private Long id;

    /** 源节点 nodeKey */
    private String fromKey;

    /** 目标节点 nodeKey */
    private String toKey;

    /** 排他分叉出边条件 */
    private ConditionDto condition;

    /** 同源出边顺序 */
    private Integer sortNo;
}
