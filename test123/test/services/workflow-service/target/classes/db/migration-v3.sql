-- ============================================================
-- workflow-service v3 表结构变更：字段挂分类下（统一「字段」概念）
--   1. field_def 加 category_id：字段归属某个维度分类（第三层就是字段）
--   2. 删除 dimension_item：取值项取消，下拉选项手填在字段 value_rule 里
--      （enum 格式从 {dimensionCode,categoryCode} 改为 {options:[...]}）
--   3. 清空测试数据（dimension/dimension_category/field_def）
-- ============================================================

ALTER TABLE field_def ADD category_id BIGINT NULL;
GO

DROP TABLE dimension_item;
GO

DELETE FROM field_def;
DELETE FROM dimension_category;
DELETE FROM dimension;
GO
