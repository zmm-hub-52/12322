package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.entity.Dimension;
import com.platform.workflow.entity.DimensionCategory;
import com.platform.workflow.entity.FieldDef;
import com.platform.workflow.service.DimensionService;
import com.platform.workflow.service.FieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 维表接口（v3：维度 / 分类 / 分类下的字段 三层维护，管理员操作）。
 * 字段挂在分类下（第三层就是字段），字段上维护默认规则，下拉选项在字段取值规则里手填。
 */
@RestController
@RequestMapping("/api/dimension")
@RequiredArgsConstructor
public class DimensionController {

    private final DimensionService dimensionService;
    private final FieldService fieldService;

    // ==================== 维度 ====================

    /**
     * 接口：GET /api/dimension —— 维度列表
     * 参数：无。
     * 返回：维度列表。
     */
    @GetMapping
    public Result<List<Dimension>> listDimensions() {
        return Result.ok(dimensionService.listDimensions());
    }

    /**
     * 接口：POST /api/dimension —— 新增维度
     * 参数：Dimension（dimensionCode 唯一、dimensionName）。
     * 返回：新建的维度。
     */
    @PostMapping
    public Result<Dimension> createDimension(@RequestBody Dimension d) {
        return Result.ok(dimensionService.createDimension(d));
    }

    /**
     * 接口：DELETE /api/dimension/{id} —— 删除维度（逻辑删除）
     * 参数：id 维度ID。
     * 返回：无。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDimension(@PathVariable Long id) {
        dimensionService.deleteDimension(id);
        return Result.ok();
    }

    // ==================== 维度分类 ====================

    /**
     * 接口：GET /api/dimension/{dimensionId}/category —— 某维度下的分类列表
     * 参数：dimensionId 维度ID。
     * 返回：分类列表。
     */
    @GetMapping("/{dimensionId}/category")
    public Result<List<DimensionCategory>> listCategories(@PathVariable Long dimensionId) {
        return Result.ok(dimensionService.listCategories(dimensionId));
    }

    /**
     * 接口：POST /api/dimension/category —— 新增维度分类
     * 参数：DimensionCategory（dimensionId、categoryCode、categoryName）。
     * 返回：新建的分类。
     */
    @PostMapping("/category")
    public Result<DimensionCategory> createCategory(@RequestBody DimensionCategory c) {
        return Result.ok(dimensionService.createCategory(c));
    }

    /**
     * 接口：DELETE /api/dimension/category/{id} —— 删除维度分类（逻辑删除）
     * 参数：id 分类ID。
     * 返回：无。
     */
    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        dimensionService.deleteCategory(id);
        return Result.ok();
    }

    // ==================== 分类下的字段（第三层） ====================

    /**
     * 接口：GET /api/dimension/category/{categoryId}/field —— 分类下的字段列表
     * 用途：维表第三栏 + 表单配置页字段库（维度→分类→字段 三级浏览的数据源）。
     * 参数：categoryId 分类ID。
     * 返回：字段列表（含取值规则/校验/必填默认值）。
     */
    @GetMapping("/category/{categoryId}/field")
    public Result<List<FieldDef>> listFields(@PathVariable Long categoryId) {
        return Result.ok(fieldService.listByCategory(categoryId));
    }

    /**
     * 接口：POST /api/dimension/category/{categoryId}/field —— 分类下新增字段
     * 参数：categoryId 分类ID；body FieldDef（fieldKey 全局唯一、fieldName、fieldType、
     *       required 默认必填、valueRule 取值规则、validation 校验、sortNo）。
     * 返回：新建的字段。
     */
    @PostMapping("/category/{categoryId}/field")
    public Result<FieldDef> createField(@PathVariable Long categoryId, @RequestBody FieldDef def) {
        def.setCategoryId(categoryId);
        return Result.ok(fieldService.create(def));
    }
}
