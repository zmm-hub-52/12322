package com.platform.workflow.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类：公共时间字段。
 * create_time / update_time 由 MyMetaObjectHandler 自动填充（见 config 包）。
 */
@Data
public abstract class BaseEntity {

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入与更新时自动填充） */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
