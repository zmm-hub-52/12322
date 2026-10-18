-- ============================================================
-- 工作流 + 动态数据表单微服务 数据库表结构（v2：DAG + Flowable 引擎驱动）
-- 数据库：SQL Server（建议 2019+）
--
-- 设计约定：
--   1. 主键 BIGINT IDENTITY(1,1) 自增
--   2. 软删除字段 enabled BIT（1=启用/未删，0=停用/已删），配合 MyBatis-Plus 逻辑删除
--   3. JSON 字段用 NVARCHAR(MAX) 存储（SQL Server 无原生 JSON 列类型）
--   4. 时间字段 DATETIME2
--   5. order/sort 是保留词，排序字段用 order_no / sort_no
--
-- v2 核心变化（相对 v1 线性模型）：
--   - process_node.node_key 即 BPMN taskId（画布节点 / 引擎任务 / 表单快照 三合一 key）
--   - 节点流转关系由 process_link（DAG 边）表达，删除 process_task
--   - 新增 process_record：流程轨迹/审批意见（业务数据自持，不依赖 Flowable 表）
--   - process_instance 只保留业务单据信息 + flowable_instance_id 映射，状态由引擎提供
-- ============================================================

-- ① 流程定义（同一 code 多版本共存，version 每次发布 +1，新老版本隔离的关键）
CREATE TABLE process_def (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    code          NVARCHAR(64)  NOT NULL,               -- 流程编码（业务标识，= Flowable process key）
    name          NVARCHAR(128) NOT NULL,               -- 流程名称
    version       INT           NOT NULL DEFAULT 1,     -- 版本号：每次发布 +1
    status        NVARCHAR(16)  NOT NULL DEFAULT 'draft', -- 状态：draft 草稿 / published 已发布 / disabled 停用
    description   NVARCHAR(500) NULL,                   -- 描述
    deployment_id NVARCHAR(64)  NULL,                   -- 发布时记录的 Flowable 部署 ID（版本溯源）
    enabled       BIT           NOT NULL DEFAULT 1,     -- 软删除
    create_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ② 流程节点（node_key = BPMN taskId，全局语义三合一）
CREATE TABLE process_node (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id  BIGINT        NOT NULL,            -- 所属流程定义
    node_key        NVARCHAR(64)  NOT NULL,            -- 节点标识（= BPMN userTask/gateway id，表单快照 key）
    node_name       NVARCHAR(128) NOT NULL,            -- 节点名称
    node_type       NVARCHAR(16)  NOT NULL,            -- 类型：start/fill/approval/parallelSplit/parallelJoin/exclusiveSplit/exclusiveJoin/end
    reject_strategy NVARCHAR(16)  NOT NULL DEFAULT 'back_prev', -- 审批节点驳回策略：back_prev/back_starter/terminate
    enabled         BIT           NOT NULL DEFAULT 1,
    create_time     DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ③ 节点连线（DAG 边；排他分叉出边携带条件）
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

-- ④ 节点字段配置（= schema 实体：每个节点的表单由这些行组成）
CREATE TABLE node_form_field (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    node_id     BIGINT        NOT NULL,               -- 所属节点
    field_key   NVARCHAR(64)  NOT NULL,               -- 引用 field_def.field_key（字段库）
    order_no    INT           NOT NULL DEFAULT 0,     -- 字段显示顺序
    mode        NVARCHAR(16)  NOT NULL DEFAULT 'editable', -- 节点级状态：editable 可编辑 / readonly 只读审批 / hidden 隐藏
    override    NVARCHAR(MAX) NULL,                   -- 节点级覆盖 JSON：{ required, valueRule, validation }（可空，空则用字段库默认）
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ⑤ 流程实例（业务单据与引擎实例的映射；状态/任务完全由 Flowable 提供）
CREATE TABLE process_instance (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id          BIGINT        NOT NULL,   -- 流程定义ID
    process_def_version     INT           NOT NULL,   -- 所用流程定义版本（标识实例跑的是哪版流程）
    form_schema_snapshot_id BIGINT        NULL,       -- 引用 form_schema_snapshot（运行期只读快照）
    flowable_instance_id    NVARCHAR(64)  NULL,       -- Flowable 引擎实例 ID（引擎驱动映射）
    biz_no                  NVARCHAR(64)  NULL,       -- 业务编号
    title                   NVARCHAR(256) NULL,       -- 实例标题
    applicant               NVARCHAR(64)  NULL,       -- 发起人
    form_data               NVARCHAR(MAX) NULL,       -- JSON：业务数据（所有动态字段的值，全流程共享一份）
    create_time             DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time             DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ⑥ 流程轨迹（每一步操作留痕：执行人/动作/意见；业务数据自持，不依赖 Flowable comment）
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

-- ⑦ 表单 schema 快照（每个流程定义版本一份，发布时生成、之后不可变；同版本实例共享）
--    snapshot_json 结构：{ schemaVersion, nodes: { nodeKey: [field...] } }，nodeKey 即 BPMN taskId
CREATE TABLE form_schema_snapshot (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id      BIGINT         NOT NULL,      -- 流程定义ID
    process_def_version INT            NOT NULL,      -- 版本号（定位「哪版流程的快照」）
    snapshot_json       NVARCHAR(MAX)  NOT NULL,      -- JSON：全流程所有节点的完整 schema（字段定义已展开、自包含）
    create_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);

-- ⑧ 全局字段库（字段定义，跨流程复用；管理员在字段维护页维护）
CREATE TABLE field_def (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    field_key   NVARCHAR(64)  NOT NULL UNIQUE,        -- 字段 key（业务语义标识：语义不同必须不同 key）
    field_name  NVARCHAR(128) NOT NULL,               -- 字段名（默认 label）
    field_type  NVARCHAR(32)  NOT NULL,               -- 中立业务类型：input/textarea/number/amount/select/radio/checkbox/date/datetime/switch/upload/user/dept
    value_rule  NVARCHAR(MAX) NULL,                   -- JSON：默认取值规则 {type: enum|codeRange|range|cascade, ...}
    validation  NVARCHAR(MAX) NULL,                   -- JSON：默认校验 {maxLength, pattern, ...}
    required    BIT           NOT NULL DEFAULT 0,     -- 默认是否必填
    sort_no     INT           NOT NULL DEFAULT 0,     -- 排序
    enabled     BIT           NOT NULL DEFAULT 1,     -- 软删除（防呆：不物理删，节点引用不失效）
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ⑨ 维度（制造业主数据维度：物料 / 质量 / 采购 ...）
CREATE TABLE dimension (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    dimension_code NVARCHAR(64)  NOT NULL UNIQUE,     -- 维度编码：material / quality / purchase
    dimension_name NVARCHAR(128) NOT NULL,            -- 维度名称：物料 / 质量 / 采购
    enabled        BIT           NOT NULL DEFAULT 1,
    create_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ⑩ 维度分类（维度下的子分类，如物料维度下的 ROH 原材料 / HALB 半成品 / FERT 成品）
CREATE TABLE dimension_category (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    dimension_id  BIGINT        NOT NULL,             -- 所属维度
    category_code NVARCHAR(64)  NOT NULL,             -- 分类编码：ROH / HALB / FERT
    category_name NVARCHAR(128) NOT NULL,             -- 分类名称：原材料 / 半成品 / 成品
    sort_no       INT           NOT NULL DEFAULT 0,
    enabled       BIT           NOT NULL DEFAULT 1,
    create_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ⑪ 维度取值项（字段可选值的实体）
CREATE TABLE dimension_item (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_id BIGINT        NOT NULL,               -- 所属分类
    item_value  NVARCHAR(128) NOT NULL,               -- 值：A01 / abc / 100
    item_label  NVARCHAR(128) NOT NULL,               -- 显示名
    sort_no     INT           NOT NULL DEFAULT 0,
    enabled     BIT           NOT NULL DEFAULT 1,     -- 软删除（防呆：历史流程实例仍能显示原值）
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ============ 索引 ============
CREATE INDEX idx_process_def_code    ON process_def(code);
CREATE INDEX idx_process_node_def    ON process_node(process_def_id);
CREATE INDEX idx_link_def            ON process_link(process_def_id);
CREATE INDEX idx_node_form_field_n   ON node_form_field(node_id);
CREATE INDEX idx_instance_snapshot   ON process_instance(form_schema_snapshot_id);
CREATE INDEX idx_instance_def        ON process_instance(process_def_id, process_def_version);
CREATE INDEX idx_record_instance     ON process_record(process_instance_id);
CREATE INDEX idx_snapshot_def        ON form_schema_snapshot(process_def_id, process_def_version);
CREATE INDEX idx_category_dimension  ON dimension_category(dimension_id);
CREATE INDEX idx_item_category       ON dimension_item(category_id);
