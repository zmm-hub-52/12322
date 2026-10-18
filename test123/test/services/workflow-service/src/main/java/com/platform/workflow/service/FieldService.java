package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.entity.FieldDef;
import com.platform.workflow.mapper.DimensionCategoryMapper;
import com.platform.workflow.mapper.FieldDefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字段服务（v3）：字段挂在维度分类下（维表第三层就是字段）。
 * 字段上维护「默认规则」（取值规则/校验/必填），节点配置时继承、节点级可覆盖。
 */
@Service
@RequiredArgsConstructor
public class FieldService {

    private final FieldDefMapper fieldDefMapper;
    private final DimensionCategoryMapper categoryMapper;

    /** 查询某分类下的字段（按排序） */
    public List<FieldDef> listByCategory(Long categoryId) {
        return fieldDefMapper.selectList(
                new LambdaQueryWrapper<FieldDef>()
                        .eq(FieldDef::getCategoryId, categoryId)
                        .orderByAsc(FieldDef::getSortNo));
    }

    /** 按 id 查询 */
    public FieldDef getById(Long id) {
        FieldDef def = fieldDefMapper.selectById(id);
        if (def == null) {
            throw new BizException("字段不存在：id=" + id);
        }
        return def;
    }

    /** 按 key 查询（全局唯一） */
    public FieldDef getByKey(String fieldKey) {
        return fieldDefMapper.selectOne(
                new LambdaQueryWrapper<FieldDef>().eq(FieldDef::getFieldKey, fieldKey));
    }

    /** 新增字段（挂在分类下；field_key 全局唯一） */
    public FieldDef create(FieldDef def) {
        if (def.getCategoryId() == null || categoryMapper.selectById(def.getCategoryId()) == null) {
            throw new BizException("分类不存在：id=" + def.getCategoryId());
        }
        if (getByKey(def.getFieldKey()) != null) {
            throw new BizException("字段 key 已存在：" + def.getFieldKey());
        }
        fieldDefMapper.insert(def);
        return def;
    }

    /** 更新字段（key 不可改，只改名称/类型/规则/校验等） */
    public FieldDef update(Long id, FieldDef def) {
        FieldDef exist = getById(id);
        def.setId(id);
        def.setFieldKey(exist.getFieldKey()); // key 是业务语义标识，不允许改
        fieldDefMapper.updateById(def);
        return def;
    }

    /**
     * 删除字段（逻辑删除，防呆）。
     * 说明：软删除后节点引用不失效；已发布版本的快照不受影响。
     */
    public void delete(Long id) {
        getById(id);
        fieldDefMapper.deleteById(id);
    }
}
