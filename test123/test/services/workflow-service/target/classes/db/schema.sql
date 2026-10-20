-- ============================================================
-- 工作流 + 动态数据表单微服务 数据库完整初始化脚本（开发阶段单文件版）
-- 数据库：SQL Server（建议 2019+）
--
-- 设计约定：
--   1. 主键 BIGINT IDENTITY(1,1) 自增
--   2. 软删除字段 enabled BIT（1=启用/未删，0=停用/已删），配合 MyBatis-Plus 逻辑删除
--   3. JSON 字段用 NVARCHAR(MAX) 存储（SQL Server 无原生 JSON 列类型）
--   4. 时间字段 DATETIME2
--   5. 中文预置数据一律 N 前缀（如 N'张三'），防乱码
--   6. order/sort 是保留词，排序字段用 order_no / sort_no
--
-- 架构要点（v3 定稿）：
--   - 维表三层 = dimension → dimension_category → field_def（字段即第三层）
--   - node_key 三合一：画布节点标识 / BPMN taskId / 表单快照 key
--   - 流程流转由 process_link（DAG 边）表达；排他条件挂在分叉出边上
--   - 运行时由 Flowable 引擎驱动；业务表只留单据与轨迹（process_instance / process_record）
-- ============================================================

-- ==================== ① 流程定义 ====================
CREATE TABLE process_def (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    code          NVARCHAR(64)  NOT NULL,               -- 流程编码（= Flowable process key）
    name          NVARCHAR(128) NOT NULL,               -- 流程名称
    version       INT           NOT NULL DEFAULT 1,     -- 版本号：每次发布 +1
    status        NVARCHAR(16)  NOT NULL DEFAULT 'draft', -- 状态：draft 草稿 / published 已发布 / disabled 停用
    description   NVARCHAR(500) NULL,                   -- 描述
    deployment_id NVARCHAR(64)  NULL,                   -- 发布时记录的 Flowable 部署 ID（版本溯源）
    published_time DATETIME2    NULL,                   -- 最近发布时间（列表「待发布改动」徽标：update_time > published_time）
    enabled       BIT           NOT NULL DEFAULT 1,     -- 软删除
    create_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ② 流程节点 ====================
CREATE TABLE process_node (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id  BIGINT        NOT NULL,            -- 所属流程定义
    node_key        NVARCHAR(64)  NOT NULL,            -- 节点标识（= BPMN userTask/gateway id，表单快照 key）
    node_name       NVARCHAR(128) NOT NULL,            -- 节点名称
    node_type       NVARCHAR(16)  NOT NULL,            -- 类型：start/fill/approval/parallelSplit/parallelJoin/exclusiveSplit/exclusiveJoin/end
    reject_strategy NVARCHAR(16)  NOT NULL DEFAULT 'back_prev', -- 审批节点驳回策略：back_prev/back_starter/terminate
    assignee_mode   NVARCHAR(16)  NOT NULL DEFAULT 'dynamic',  -- 处理人模式：fixed 固定 / dynamic 动态（上一环节选下一人）
    fixed_assignee  NVARCHAR(64)  NULL,                -- 固定处理人（assignee_mode=fixed 时生效，存 username）
    enabled         BIT           NOT NULL DEFAULT 1,
    create_time     DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ③ 节点连线（DAG 边） ====================
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

-- ==================== ④ 节点字段配置 ====================
CREATE TABLE node_form_field (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    node_id     BIGINT        NOT NULL,               -- 所属节点
    field_key   NVARCHAR(64)  NOT NULL,               -- 引用 field_def.field_key（字段库）
    order_no    INT           NOT NULL DEFAULT 0,     -- 字段显示顺序
    mode        NVARCHAR(16)  NOT NULL DEFAULT 'editable', -- 节点级状态：editable 可编辑 / readonly 只读审批 / hidden 隐藏
    override    NVARCHAR(MAX) NULL,                   -- 节点级覆盖 JSON：{ required, valueRule, validation }
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑤ 流程实例 ====================
CREATE TABLE process_instance (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id          BIGINT        NOT NULL,   -- 流程定义ID
    process_def_version     INT           NOT NULL,   -- 所用流程定义版本
    form_schema_snapshot_id BIGINT        NULL,       -- 引用 form_schema_snapshot（运行期只读快照）
    flowable_instance_id    NVARCHAR(64)  NULL,       -- Flowable 引擎实例 ID
    biz_no                  NVARCHAR(64)  NULL,       -- 业务编号
    title                   NVARCHAR(256) NULL,       -- 实例标题
    applicant               NVARCHAR(64)  NULL,       -- 发起人
    form_data               NVARCHAR(MAX) NULL,       -- JSON：业务数据（全流程共享一份）
    status                  NVARCHAR(16)  NOT NULL DEFAULT 'active', -- 状态：active 进行中 / finished 正常走完 / terminated 驳回终止
    end_time                DATETIME2     NULL,       -- 结束时间（结束时写入）
    create_time             DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time             DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑥ 流程轨迹 ====================
CREATE TABLE process_record (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_instance_id BIGINT         NOT NULL,       -- 业务实例
    node_key            NVARCHAR(64)   NOT NULL,       -- 步骤节点（= BPMN taskId）
    node_name           NVARCHAR(128)  NULL,           -- 节点名称（记录时冗余）
    action_type         NVARCHAR(16)   NOT NULL,       -- 动作：start / submit / approve / reject / terminate
    executor            NVARCHAR(64)   NULL,           -- 执行人
    comment             NVARCHAR(1000) NULL,           -- 审批意见/备注（业务数据）
    flowable_task_id    NVARCHAR(64)   NULL,           -- 冗余引擎任务 id
    enabled             BIT            NOT NULL DEFAULT 1,
    create_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    update_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑦ 表单 schema 快照 ====================
CREATE TABLE form_schema_snapshot (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    process_def_id      BIGINT         NOT NULL,      -- 流程定义ID
    process_def_version INT            NOT NULL,      -- 版本号
    snapshot_json       NVARCHAR(MAX)  NOT NULL,      -- JSON：全流程所有节点完整 schema（按 nodeKey 组织）
    create_time         DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑧ 字段定义（维表第三层） ====================
CREATE TABLE field_def (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_id BIGINT        NOT NULL,               -- 所属维度分类
    field_key   NVARCHAR(64)  NOT NULL UNIQUE,        -- 字段 key（全局唯一）
    field_name  NVARCHAR(128) NOT NULL,               -- 字段名
    description NVARCHAR(500) NULL,                   -- 字段描述（填写时 placeholder：字段名|字段描述）
    field_type  NVARCHAR(32)  NOT NULL,               -- 类型：input/textarea/number/select/date/boolean
    field_rule  NVARCHAR(MAX) NULL,                   -- JSON 字段规则（取值+校验二合一）：{type:enum|codeRange|range|pattern, ...}
    required    BIT           NOT NULL DEFAULT 0,     -- 默认必填（节点可覆盖）
    sort_no     INT           NOT NULL DEFAULT 0,
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑨ 维度 ====================
CREATE TABLE dimension (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    dimension_code NVARCHAR(64)  NOT NULL UNIQUE,
    dimension_name NVARCHAR(128) NOT NULL,
    enabled        BIT           NOT NULL DEFAULT 1,
    create_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time    DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑩ 维度分类 ====================
CREATE TABLE dimension_category (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    dimension_id  BIGINT        NOT NULL,
    category_code NVARCHAR(64)  NOT NULL,
    category_name NVARCHAR(128) NOT NULL,
    sort_no       INT           NOT NULL DEFAULT 0,
    enabled       BIT           NOT NULL DEFAULT 1,
    create_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time   DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑪ 角色 ====================
CREATE TABLE sys_role (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_code   NVARCHAR(32)  NOT NULL UNIQUE,
    role_name   NVARCHAR(64)  NOT NULL,
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== ⑫ 用户 ====================
CREATE TABLE sys_user (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    username    NVARCHAR(64)  NOT NULL UNIQUE,
    name        NVARCHAR(64)  NOT NULL,
    role_id     BIGINT        NOT NULL,
    password    NVARCHAR(128) NULL,                   -- 测试用途免密（保留字段）
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ==================== 索引 ====================
CREATE INDEX idx_process_def_code   ON process_def(code);
CREATE INDEX idx_process_node_def   ON process_node(process_def_id);
CREATE INDEX idx_link_def           ON process_link(process_def_id);
CREATE INDEX idx_node_form_field_n  ON node_form_field(node_id);
CREATE INDEX idx_instance_snapshot  ON process_instance(form_schema_snapshot_id);
CREATE INDEX idx_instance_def       ON process_instance(process_def_id, process_def_version);
CREATE INDEX idx_record_instance    ON process_record(process_instance_id);
CREATE INDEX idx_snapshot_def       ON form_schema_snapshot(process_def_id, process_def_version);
CREATE INDEX idx_field_category     ON field_def(category_id);
CREATE INDEX idx_category_dimension ON dimension_category(dimension_id);

-- ==================== 预置数据（N 前缀防乱码） ====================

-- 角色
INSERT INTO sys_role (role_code, role_name) VALUES
(N'admin', N'系统管理员'),
(N'buyer', N'采购员'),
(N'manager', N'部门经理'),
(N'finance', N'财务'),
(N'quality', N'质量工程师');

-- 测试用户（免密，流程测试身份切换）
INSERT INTO sys_user (username, name, role_id) VALUES
(N'admin', N'管理员', (SELECT id FROM sys_role WHERE role_code=N'admin')),
(N'zhangsan', N'张三', (SELECT id FROM sys_role WHERE role_code=N'buyer')),
(N'lisi', N'李四', (SELECT id FROM sys_role WHERE role_code=N'buyer')),
(N'manager_li', N'李经理', (SELECT id FROM sys_role WHERE role_code=N'manager')),
(N'manager_wang', N'王经理', (SELECT id FROM sys_role WHERE role_code=N'manager')),
(N'finance_wang', N'王财务', (SELECT id FROM sys_role WHERE role_code=N'finance')),
(N'quality_zhao', N'赵质量', (SELECT id FROM sys_role WHERE role_code=N'quality'));

-- 维度与分类（示例）
INSERT INTO dimension (dimension_code, dimension_name) VALUES
(N'material', N'物料'),
(N'quality', N'质量'),
(N'common', N'通用');

INSERT INTO dimension_category (dimension_id, category_code, category_name, sort_no) VALUES
((SELECT id FROM dimension WHERE dimension_code=N'material'), N'ROH', N'原材料', 1),
((SELECT id FROM dimension WHERE dimension_code=N'material'), N'HALB', N'半成品', 2),
((SELECT id FROM dimension WHERE dimension_code=N'quality'), N'INSPECT', N'检验项目', 1),
((SELECT id FROM dimension WHERE dimension_code=N'common'), N'GENERAL', N'通用信息', 1);
