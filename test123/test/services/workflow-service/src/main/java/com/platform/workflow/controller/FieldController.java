package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.entity.FieldDef;
import com.platform.workflow.service.FieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 字段接口（v3：字段挂在分类下，见 DimensionController 的 /category/{id}/field）。
 * 这里保留按 id 的详情/更新/停用。
 */
@RestController
@RequestMapping("/api/field")
@RequiredArgsConstructor
public class FieldController {

    private final FieldService fieldService;

    /**
     * 接口：GET /api/field/{id} —— 字段详情
     * 参数：id 字段ID。
     * 返回：字段定义（配置弹层回显用）。
     */
    @GetMapping("/{id}")
    public Result<FieldDef> get(@PathVariable Long id) {
        return Result.ok(fieldService.getById(id));
    }

    /**
     * 接口：PUT /api/field/{id} —— 更新字段
     * 说明：fieldKey 不可改（业务语义标识，被节点引用）；其余字段可改。
     * 参数：id 字段ID；body 要更新的字段内容。
     * 返回：更新后的字段定义。
     */
    @PutMapping("/{id}")
    public Result<FieldDef> update(@PathVariable Long id, @RequestBody FieldDef def) {
        return Result.ok(fieldService.update(id, def));
    }

    /**
     * 接口：DELETE /api/field/{id} —— 停用字段（逻辑删除，防呆）
     * 说明：软删除后节点引用不失效，已发布版本的快照不受影响。
     * 参数：id 字段ID。
     * 返回：无。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fieldService.delete(id);
        return Result.ok();
    }
}
