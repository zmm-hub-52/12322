package com.platform.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.common.Result;
import com.platform.workflow.dto.UserViewDto;
import com.platform.workflow.entity.SysRole;
import com.platform.workflow.entity.SysUser;
import com.platform.workflow.mapper.SysRoleMapper;
import com.platform.workflow.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证接口（轻量用户体系，测试流程身份切换用）。
 * 免密登录：按 username 登录；后续如需密码校验再加。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    /** 登录请求（测试用途免密，password 预留） */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * 接口：POST /api/auth/login —— 登录（免密，按用户名）
     * 参数：{ username }。
     * 返回：用户信息（含角色编码/名称）。
     */
    @PostMapping("/login")
    public Result<UserViewDto> login(@RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            throw new BizException("用户不存在：" + req.getUsername());
        }
        return Result.ok(toView(user));
    }

    /**
     * 接口：GET /api/auth/users —— 用户列表（登录页下拉选择身份用）
     * 返回：全部用户（含角色信息）。
     */
    @GetMapping("/users")
    public Result<List<UserViewDto>> users() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        return Result.ok(users.stream().map(this::toView).collect(Collectors.toList()));
    }

    /** 角色列表（后续审批人选择等场景用） */
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.ok(roleMapper.selectList(null));
    }

    private UserViewDto toView(SysUser user) {
        SysRole role = roleMapper.selectById(user.getRoleId());
        UserViewDto dto = new UserViewDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setName(user.getName());
        dto.setRoleCode(role != null ? role.getRoleCode() : null);
        dto.setRoleName(role != null ? role.getRoleName() : null);
        return dto;
    }
}
