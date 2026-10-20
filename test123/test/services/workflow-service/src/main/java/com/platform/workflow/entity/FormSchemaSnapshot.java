package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ⑥ 表单 schema 快照。
 * 每个流程定义版本一份，发布时生成、之后不可变；同版本实例共享一份（避免冗余）。
 * 运行期前端只读这份快照，不再实时查配置表。
 */
@Data
@TableName("form_schema_snapshot")
public class FormSchemaSnapshot {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程定义ID */
    private Long processDefId;

    /** 版本号（定位「哪版流程的快照」） */
    private Integer processDefVersion;

    /** JSON：全流程所有节点的完整 schema（字段定义已展开、自包含） */
    private String snapshotJson;

    /** JSON：可执行图快照（节点 + 连线 + 处理人元数据；运行期只读，与配置活表解耦） */
    private String graphJson;

    /** 生成时间（快照不可变，无 update_time） */
    private LocalDateTime createTime;
}
