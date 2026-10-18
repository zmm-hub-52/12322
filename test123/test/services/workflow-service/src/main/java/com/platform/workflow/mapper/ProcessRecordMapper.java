package com.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.workflow.entity.ProcessRecord;
import org.apache.ibatis.annotations.Mapper;

/** 流程轨迹 Mapper */
@Mapper
public interface ProcessRecordMapper extends BaseMapper<ProcessRecord> {
}
