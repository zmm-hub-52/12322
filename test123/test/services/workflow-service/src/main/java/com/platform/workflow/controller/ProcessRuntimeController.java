package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.dto.ApproveRequest;
import com.platform.workflow.dto.FinishedInstanceDto;
import com.platform.workflow.dto.StartRequest;
import com.platform.workflow.dto.SubmitRequest;
import com.platform.workflow.dto.TaskViewDto;
import com.platform.workflow.entity.ProcessInstance;
import com.platform.workflow.entity.ProcessRecord;
import com.platform.workflow.service.ProcessRuntimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程运行接口（v2：完全由 Flowable 引擎驱动）。
 * 发起 / 待办 / 填写提交 / 审批 / 轨迹 / 获取节点 schema。
 * 任务 id 均为引擎任务 id；node_key = 引擎 taskDefinitionKey = 表单快照 key。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessRuntimeController {

    private final ProcessRuntimeService runtimeService;

    /**
     * 接口：POST /api/instance/start —— 发起流程
     * 参数：StartRequest（processDefId、applicant、bizNo、title、formData 初始表单数据）。
     * 返回：业务实例（含 flowableInstanceId）。
     */
    @PostMapping("/instance/start")
    public Result<ProcessInstance> start(@Valid @RequestBody StartRequest req) {
        return Result.ok(runtimeService.start(req));
    }

    /**
     * 接口：GET /api/task/my —— 我的待办（引擎任务 + 业务上下文）
     * 参数：assignee 处理人（query）。
     * 返回：待办列表（taskId 为引擎任务 id，nodeKey 即表单快照 key）。
     */
    @GetMapping("/task/my")
    public Result<List<TaskViewDto>> myTasks(@RequestParam String assignee) {
        return Result.ok(runtimeService.myTasks(assignee));
    }

    /**
     * 接口：POST /api/task/submit —— 填写节点提交
     * 说明：字段值作为流程变量完成引擎任务，自动流转（并行/排他由引擎路由）。
     * 参数：SubmitRequest（taskId 引擎任务 id、formData 表单数据）。
     * 返回：无。
     */
    @PostMapping("/task/submit")
    public Result<Void> submit(@Valid @RequestBody SubmitRequest req) {
        runtimeService.submit(req);
        return Result.ok();
    }

    /**
     * 接口：POST /api/task/approve —— 审批（通过 / 驳回）
     * 说明：approved 变量驱动 BPMN 批准/驳回线；驳回按节点策略（退上一步/回发起人/终止流程）走。
     *       审批意见写入业务轨迹表 process_record。
     * 参数：ApproveRequest（taskId、approved、comment）。
     * 返回：无。
     */
    @PostMapping("/task/approve")
    public Result<Void> approve(@Valid @RequestBody ApproveRequest req) {
        runtimeService.approve(req);
        return Result.ok();
    }

    /**
     * 接口：GET /api/instance/{id}/schema —— 获取实例当前节点的表单 schema
     * 参数：id 业务实例ID；taskId 引擎任务ID（并行时定位具体任务用）。
     * 返回：Map —— { schemaVersion, taskId, nodeKey, nodeName, nodeType, fields, formData, finished }；
     *       finished=true 表示流程已结束（endActivityId 标识结束方式）。
     */
    @GetMapping("/instance/{id}/schema")
    public Result<Map<String, Object>> getNodeSchema(@PathVariable Long id,
                                                     @RequestParam(required = false) String taskId) {
        return Result.ok(runtimeService.getNodeSchema(id, taskId));
    }

    /**
     * 接口：GET /api/instance/{id}/records —— 实例轨迹（每步操作/审批意见）
     * 参数：id 业务实例ID。
     * 返回：轨迹列表（时间正序）。
     */
    @GetMapping("/instance/{id}/records")
    public Result<List<ProcessRecord>> records(@PathVariable Long id) {
        return Result.ok(runtimeService.records(id));
    }

    /**
     * 接口：GET /api/instance/finished —— 已完结实例列表（正常走完 + 驳回终止）
     * 返回：列表（含流程名、起止时间、状态）。
     */
    @GetMapping("/instance/finished")
    public Result<List<FinishedInstanceDto>> finishedInstances() {
        return Result.ok(runtimeService.listFinished());
    }

    /**
     * 接口：GET /api/instance/{id}/form-data-export —— 导出实例 formData 为 xlsx（一行，列头=字段英文 key）
     */
    @GetMapping("/instance/{id}/form-data-export")
    public ResponseEntity<byte[]> exportFormData(@PathVariable Long id) {
        byte[] bytes = runtimeService.exportFormData(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=form-data-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
