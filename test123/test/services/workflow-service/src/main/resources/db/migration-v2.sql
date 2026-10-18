-- ============================================================
-- workflow-service v2 表结构变更脚本（迁移自 v1 线性模型）
-- 变更内容：
--   1. process_node：删 sort_no，加 reject_strategy（驳回策略）
--   2. 新增 process_link：节点连线表（DAG 边 + 排他条件）
--   3. 新增 process_record：流程轨迹/审批意见（业务数据，不依赖引擎表）
--   4. process_def：加 deployment_id（发布时记录的 Flowable 部署 ID）
--   5. process_instance：加 flowable_instance_id，删 process_status/current_node_id
--      （状态完全由 Flowable 引擎提供）
--   6. 删除 process_task（任务=引擎任务，业务表不再维护）
-- ============================================================

-- 1. process_node 改造（先删 sort_no 的默认约束，再删列）
DECLARE @df_name NVARCHAR(200);
SELECT @df_name = d.name
FROM sys.default_constraints d
WHERE d.parent_object_id = OBJECT_ID('process_node')
  AND d.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('process_node') AND name = 'sort_no');
IF @df_name IS NOT NULL
    EXEC('ALTER TABLE process_node DROP CONSTRAINT ' + @df_name);
GO
ALTER TABLE process_node DROP COLUMN sort_no;
GO
ALTER TABLE process_node ADD reject_strategy NVARCHAR(16) NOT NULL CONSTRAINT DF_process_node_reject DEFAULT 'back_prev';
GO

-- 2. 节点连线表（DAG 边；排他条件挂在分叉出边上）
CREATE TABLE process_link (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id BIGINT        NOT NULL,             -- 所属流程定义
    from_node_id   BIGINT        NOT NULL,             -- 源节点
    to_node_id     BIGINT        NOT NULL,             -- 目标节点
    condition_json NVARCHAR(MAX) NULL,                 -- 排他分支条件 JSON：{ fieldKey, operator, value, isDefault }
    sort_no        INT           NOT NULL DEFAULT 0,   -- 同源出边顺序（条件匹配优先级）
    enabled        BIT           NOT NULL DEFAULT 1,
    create_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX idx_link_def ON process_link(process_def_id);
GO

-- 3. 流程轨迹表（每一步操作留痕：执行人/动作/意见；业务数据自持，不依赖 Flowable comment）
CREATE TABLE process_record (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_instance_id BIGINT         NOT NULL,       -- 业务实例
    node_key            NVARCHAR(64)   NOT NULL,       -- 步骤节点（= BPMN taskId）
    node_name           NVARCHAR(128)  NULL,           -- 节点名称（记录时冗余）
    action_type         NVARCHAR(16)   NOT NULL,       -- 动作：start / submit / approve / reject / terminate
    executor            NVARCHAR(64)   NULL,           -- 执行人
    comment             NVARCHAR(1000) NULL,           -- 审批意见/备注（业务数据）
    flowable_task_id    NVARCHAR(64)   NULL,           -- 冗余引擎任务 id（溯源）
    enabled             BIT            NOT NULL DEFAULT 1,
    create_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    update_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX idx_record_instance ON process_record(process_instance_id);
GO

-- 4. process_def 加部署 id
ALTER TABLE process_def ADD deployment_id NVARCHAR(64) NULL;
GO

-- 5. process_instance 瘦身 + 引擎映射
ALTER TABLE process_instance ADD flowable_instance_id NVARCHAR(64) NULL;
GO
ALTER TABLE process_instance DROP COLUMN process_status;
GO
ALTER TABLE process_instance DROP COLUMN current_node_id;
GO

-- 6. 删除业务任务表（任务完全交给 Flowable）
DROP TABLE process_task;
GO
