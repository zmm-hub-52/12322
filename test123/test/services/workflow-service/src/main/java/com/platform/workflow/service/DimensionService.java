package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.entity.Dimension;
import com.platform.workflow.entity.DimensionCategory;
import com.platform.workflow.mapper.DimensionCategoryMapper;
import com.platform.workflow.mapper.DimensionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 维表服务（v3）：维度 → 分类 → 字段 三层（字段见 {@link FieldService}）。
 * 字段挂在分类下，字段上维护默认规则；下拉选项由运维在字段取值规则里手填。
 */
@Service
@RequiredArgsConstructor
public class DimensionService {

    private final DimensionMapper dimensionMapper;
    private final DimensionCategoryMapper categoryMapper;

    // ============ 维度 ============

    public List<Dimension> listDimensions() {
        return dimensionMapper.selectList(
                new LambdaQueryWrapper<Dimension>().orderByAsc(Dimension::getId));
    }

    public Dimension createDimension(Dimension d) {
        Long count = dimensionMapper.selectCount(
                new LambdaQueryWrapper<Dimension>().eq(Dimension::getDimensionCode, d.getDimensionCode()));
        if (count > 0) {
            throw new BizException("维度编码已存在：" + d.getDimensionCode());
        }
        dimensionMapper.insert(d);
        return d;
    }

    public void deleteDimension(Long id) {
        if (dimensionMapper.selectById(id) == null) {
            throw new BizException("维度不存在：id=" + id);
        }
        dimensionMapper.deleteById(id); // 逻辑删除
    }

    // ============ 维度分类 ============

    /** 查询某维度下的分类 */
    public List<DimensionCategory> listCategories(Long dimensionId) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<DimensionCategory>()
                        .eq(DimensionCategory::getDimensionId, dimensionId)
                        .orderByAsc(DimensionCategory::getSortNo));
    }

    public DimensionCategory createCategory(DimensionCategory c) {
        if (dimensionMapper.selectById(c.getDimensionId()) == null) {
            throw new BizException("维度不存在：id=" + c.getDimensionId());
        }
        categoryMapper.insert(c);
        return c;
    }

    public void deleteCategory(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BizException("分类不存在：id=" + id);
        }
        categoryMapper.deleteById(id); // 逻辑删除
    }
}
