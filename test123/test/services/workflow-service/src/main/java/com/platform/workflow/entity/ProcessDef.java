package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ① 流程定义。
 * 同一 code 多版本共存：每次发布 version +1，新老版本隔离的关键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_def")
public class ProcessDef extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程编码（业务标识） */
    private String code;

    /** 流程名称 */
    private String name;

    /** 版本号：每次发布 +1 */
    private Integer version;

    /** 状态：draft 草稿 / published 已发布 / disabled 停用 */
    private String status;

    /** 描述 */
    private String description;

    /** 发布时记录的 Flowable 部署 ID（版本溯源） */
    private String deploymentId;

    /** 软删除：1=启用 0=停用（配合全局逻辑删除配置） */
    @TableLogic
    private Boolean enabled;
}
