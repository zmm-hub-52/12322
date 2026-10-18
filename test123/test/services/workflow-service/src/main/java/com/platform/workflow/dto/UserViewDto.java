package com.platform.workflow.dto;

import lombok.Data;

/** 用户视图（含角色信息，前端登录/展示用） */
@Data
public class UserViewDto {

    private Long id;

    private String username;

    private String name;

    /** 角色编码：admin/buyer/manager/finance/quality */
    private String roleCode;

    /** 角色名称 */
    private String roleName;
}
