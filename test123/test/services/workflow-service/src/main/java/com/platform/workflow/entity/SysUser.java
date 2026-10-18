package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户（轻量用户体系：测试流程身份切换，免密登录） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名（zhangsan） */
    private String username;

    /** 显示名（张三） */
    private String name;

    /** 角色 */
    private Long roleId;

    /** 密码（测试用途免密，字段保留后续可启用） */
    private String password;

    @TableLogic
    private Boolean enabled;
}
