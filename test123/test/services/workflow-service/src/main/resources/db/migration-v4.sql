-- ============================================================
-- workflow-service v4：轻量用户体系（测试流程身份切换用）
--   sys_role 角色字典 + sys_user 用户（免密，测试用途）
-- ============================================================

CREATE TABLE sys_role (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_code   NVARCHAR(32)  NOT NULL UNIQUE,   -- admin/buyer/manager/finance/quality
    role_name   NVARCHAR(64)  NOT NULL,
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
GO

CREATE TABLE sys_user (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    username    NVARCHAR(64)  NOT NULL UNIQUE,   -- 登录名（zhangsan）
    name        NVARCHAR(64)  NOT NULL,          -- 显示名（张三）
    role_id     BIGINT        NOT NULL,          -- 角色
    password    NVARCHAR(128) NULL,              -- 测试用途免密（保留字段，后续可启用）
    enabled     BIT           NOT NULL DEFAULT 1,
    create_time DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    update_time DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
GO

-- 预置角色
INSERT INTO sys_role (role_code, role_name) VALUES
('admin', '系统管理员'),
('buyer', '采购员'),
('manager', '部门经理'),
('finance', '财务'),
('quality', '质量工程师');
GO

-- 预置测试用户
INSERT INTO sys_user (username, name, role_id) VALUES
('admin', '管理员', (SELECT id FROM sys_role WHERE role_code='admin')),
('zhangsan', '张三', (SELECT id FROM sys_role WHERE role_code='buyer')),
('lisi', '李四', (SELECT id FROM sys_role WHERE role_code='buyer')),
('manager_li', '李经理', (SELECT id FROM sys_role WHERE role_code='manager')),
('manager_wang', '王经理', (SELECT id FROM sys_role WHERE role_code='manager')),
('finance_wang', '王财务', (SELECT id FROM sys_role WHERE role_code='finance')),
('quality_zhao', '赵质量', (SELECT id FROM sys_role WHERE role_code='quality'));
GO
