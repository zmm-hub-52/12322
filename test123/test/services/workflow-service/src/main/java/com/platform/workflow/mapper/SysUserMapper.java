package com.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.workflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户 Mapper */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
