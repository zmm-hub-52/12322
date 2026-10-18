package com.platform.workflow.dto;

import lombok.Data;

/** 排他分支条件（与前端 BranchCondition 对齐） */
@Data
public class ConditionDto {

    /** 条件字段 key */
    private String fieldKey;

    /** 字段名（冗余展示） */
    private String fieldName;

    /** 比较符：gt/gte/lt/lte/eq/neq/contains */
    private String operator;

    /** 比较值 */
    private String value;

    /** 是否默认支（所有条件都不满足时走这条） */
    private Boolean isDefault;
}
