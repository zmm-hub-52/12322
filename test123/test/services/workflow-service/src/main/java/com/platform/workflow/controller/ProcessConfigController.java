package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.dto.GraphDto;
import com.platform.workflow.entity.NodeFormField;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.service.ProcessConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程配置接口（对应前端「页面1：配置流程 / 页面2：给节点配置动态表单」）。
 * 发布前全部是草稿编辑；发布后版本 +1、生成该版本表单快照并编译部署 BPMN 给 Flowable，运行实例只读快照。
 */
@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
public class ProcessConfigController {

    private final ProcessConfigService configService;

    // ==================== 流程定义 ====================

    /**
     * 接口：GET /api/process —— 流程列表
     * 参数：无。
     * 返回：流程列表（含草稿与已发布）。
     */
    @GetMapping
    public Result<List<ProcessDef>> listDefs() {
        return Result.ok(configService.listDefs());
    }

    /**
     * 接口：GET /api/process/{id} —— 流程详情
     * 参数：id 流程ID。
     * 返回：流程定义。
     */
    @GetMapping("/{id}")
    public Result<ProcessDef> getDef(@PathVariable Long id) {
        return Result.ok(configService.getDef(id));
    }

    /**
     * 接口：POST /api/process —— 新建流程
     * 参数：ProcessDef（code 唯一、name、description）。
     * 返回：新建的流程定义（version=1 草稿）。
     */
    @PostMapping
    public Result<ProcessDef> createDef(@RequestBody ProcessDef def) {
        return Result.ok(configService.createDef(def));
    }

    /**
     * 接口：PUT /api/process/{id} —— 更新流程基本信息
     * 参数：id 流程ID；body 要更新的内容（code 不可改）。
     * 返回：更新后的流程定义。
     */
    @PutMapping("/{id}")
    public Result<ProcessDef> updateDef(@PathVariable Long id, @RequestBody ProcessDef def) {
        return Result.ok(configService.updateDef(id, def));
    }

    /**
     * 接口：DELETE /api/process/{id} —— 删除流程（逻辑删除）
     * 参数：id 流程ID。
     * 返回：无。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDef(@PathVariable Long id) {
        configService.deleteDef(id);
        return Result.ok();
    }

    /**
     * 接口：POST /api/process/{id}/publish —— 发布流程
     * 说明：版本 +1 → 生成表单快照 → 编译 BPMN XML 部署给 Flowable（key=code，每版本一次部署）。
     * 参数：id 流程ID。
     * 返回：发布后的流程定义（含新版本号与 deploymentId）。
     */
    @PostMapping("/{id}/publish")
    public Result<ProcessDef> publish(@PathVariable Long id) {
        return Result.ok(configService.publish(id));
    }

    /**
     * 接口：GET /api/process/{id}/schema —— 最新发布版本的表单快照（发起流程预览表单用）
     * 参数：id 流程ID。
     * 返回：{ schemaVersion, nodes: { nodeKey: [fields...] } }。
     */
    @GetMapping("/{id}/schema")
    public Result<Map<String, Object>> schema(@PathVariable Long id) {
        return Result.ok(configService.getLatestSchema(id));
    }

    // ==================== 图（节点 + 连线） ====================

    /**
     * 接口：GET /api/process/{defId}/graph —— 读取整图
     * 参数：defId 流程ID。
     * 返回：{ nodes: [{id,nodeKey,nodeName,nodeType,rejectStrategy}], links: [{fromKey,toKey,condition,sortNo}] }。
     */
    @GetMapping("/{defId}/graph")
    public Result<GraphDto> getGraph(@PathVariable Long defId) {
        return Result.ok(configService.getGraph(defId));
    }

    /**
     * 接口：PUT /api/process/{defId}/graph —— 保存整图（草稿）
     * 说明：节点按 nodeKey 更新/新增，缺失的软删；连线全量重建。
     * 参数：defId 流程ID；body GraphDto。
     * 返回：保存后的整图。
     */
    @PutMapping("/{defId}/graph")
    public Result<GraphDto> saveGraph(@PathVariable Long defId, @RequestBody GraphDto dto) {
        return Result.ok(configService.saveGraph(defId, dto));
    }

    // ==================== 节点字段 ====================

    /**
     * 接口：GET /api/process/nodes/{nodeId}/fields —— 节点字段列表
     * 参数：nodeId 节点ID。
     * 返回：节点字段列表（含字段定义信息 + 节点级覆盖 override）。
     */
    @GetMapping("/nodes/{nodeId}/fields")
    public Result<List<NodeFormField>> listNodeFields(@PathVariable Long nodeId) {
        return Result.ok(configService.listNodeFields(nodeId));
    }

    /**
     * 接口：POST /api/process/nodes/{nodeId}/fields —— 给节点添加字段
     * 参数：nodeId 节点ID；body NodeFormField（fieldKey、mode、override、orderNo）。
     * 返回：新建的节点字段。
     */
    @PostMapping("/nodes/{nodeId}/fields")
    public Result<NodeFormField> createNodeField(@PathVariable Long nodeId, @RequestBody NodeFormField field) {
        return Result.ok(configService.createNodeField(nodeId, field));
    }

    /**
     * 接口：PUT /api/process/fields/{id} —— 更新节点字段
     * 参数：id 节点字段ID；body 要更新的内容。
     * 返回：更新后的节点字段。
     */
    @PutMapping("/fields/{id}")
    public Result<NodeFormField> updateNodeField(@PathVariable Long id, @RequestBody NodeFormField field) {
        return Result.ok(configService.updateNodeField(id, field));
    }

    /**
     * 接口：DELETE /api/process/fields/{id} —— 删除节点字段（逻辑删除）
     * 参数：id 节点字段ID。
     * 返回：无。
     */
    @DeleteMapping("/fields/{id}")
    public Result<Void> deleteNodeField(@PathVariable Long id) {
        configService.deleteNodeField(id);
        return Result.ok();
    }
}
