package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.dto.ApproveRequest;
import com.platform.workflow.dto.StartRequest;
import com.platform.workflow.dto.SubmitRequest;
import com.platform.workflow.dto.TaskViewDto;
import com.platform.workflow.entity.FormSchemaSnapshot;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.entity.ProcessInstance;
import com.platform.workflow.entity.ProcessNode;
import com.platform.workflow.entity.ProcessRecord;
import com.platform.workflow.mapper.FormSchemaSnapshotMapper;
import com.platform.workflow.mapper.ProcessDefMapper;
import com.platform.workflow.mapper.ProcessInstanceMapper;
import com.platform.workflow.mapper.ProcessNodeMapper;
import com.platform.workflow.mapper.ProcessRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程运行服务（v2：完全由 Flowable 引擎驱动）。
 *
 * 关键机制：
 * - 业务实例表只存「单据信息 + flowable_instance_id 映射 + 表单数据」；
 *   任务/状态/路由全部来自引擎（并行分叉自动多任务、排他按变量路由、驳回按 BPMN 已接好的线走）；
 * - node_key 三合一：taskDefinitionKey = nodeKey = 表单快照 key，零映射直接取 schema；
 * - 每一步操作写入 process_record 轨迹表（执行人/动作/意见，业务数据自持）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeService {

    private final ProcessDefMapper defMapper;
    private final ProcessNodeMapper nodeMapper;
    private final ProcessInstanceMapper instanceMapper;
    private final FormSchemaSnapshotMapper snapshotMapper;
    private final ProcessRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // Flowable 引擎 API
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    // ============ 发起 ============

    /**
     * 发起流程：
     * 1. 建业务实例（记录单据信息 + 快照引用）；
     * 2. 以 defCode 为 processKey、业务实例 id 为 businessKey 启动引擎实例，
     *    表单字段值全部作为流程变量（排他条件路由依赖）；
     * 3. 写 start 轨迹。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstance start(StartRequest req) {
        ProcessDef def = defMapper.selectById(req.getProcessDefId());
        if (def == null || !"published".equals(def.getStatus())) {
            throw new BizException("流程不存在或未发布");
        }
        FormSchemaSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<FormSchemaSnapshot>()
                        .eq(FormSchemaSnapshot::getProcessDefId, def.getId())
                        .eq(FormSchemaSnapshot::getProcessDefVersion, def.getVersion()));
        if (snapshot == null) {
            throw new BizException("流程快照不存在，请重新发布");
        }

        // 业务实例
        ProcessInstance instance = new ProcessInstance();
        instance.setProcessDefId(def.getId());
        instance.setProcessDefVersion(def.getVersion());
        instance.setFormSchemaSnapshotId(snapshot.getId());
        instance.setBizNo(req.getBizNo());
        instance.setTitle(req.getTitle());
        instance.setApplicant(req.getApplicant());
        instance.setFormData(toJson(req.getFormData() == null ? Map.of() : req.getFormData()));
        instanceMapper.insert(instance);

        // 流程变量（字段值直接作变量，供排他条件表达式使用）
        Map<String, Object> vars = new HashMap<>();
        vars.put("applicant", req.getApplicant());
        vars.put("bizNo", req.getBizNo());
        vars.put("title", req.getTitle());
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
        }
        // 各节点处理人变量（BPMN userTask flowable:assignee 读取）：
        // fill 节点默认发起人；审批节点由请求 assignees 指定
        List<ProcessNode> defNodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ProcessNode>().eq(ProcessNode::getProcessDefId, def.getId()));
        for (ProcessNode n : defNodes) {
            if ("fill".equals(n.getNodeType())) {
                vars.put("assignee_" + n.getNodeKey(),
                        req.getAssignees() != null && req.getAssignees().containsKey(n.getNodeKey())
                                ? req.getAssignees().get(n.getNodeKey()) : req.getApplicant());
            } else if ("approval".equals(n.getNodeType())) {
                String user = req.getAssignees() != null ? req.getAssignees().get(n.getNodeKey()) : null;
                if (user == null || user.isBlank()) {
                    throw new BizException("未指定审批节点处理人：" + n.getNodeName());
                }
                vars.put("assignee_" + n.getNodeKey(), user);
            }
        }

        // 引擎启动：businessKey = 业务实例 id 字符串
        org.flowable.engine.runtime.ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                def.getCode(), String.valueOf(instance.getId()), vars);
        instance.setFlowableInstanceId(pi.getId());
        instanceMapper.updateById(instance);

        record(instance.getId(), "start", "start", "开始", req.getApplicant(), null, null);
        log.info("[start] 业务实例 {} 已启动，引擎实例 {}", instance.getId(), pi.getId());
        return instance;
    }

    // ============ 填写节点提交 ============

    /**
     * 填写节点提交：字段值作为流程变量完成引擎任务（自动流转），
     * 合并进业务实例 formData，写 submit 轨迹。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(SubmitRequest req) {
        Task task = requireTask(req.getTaskId());
        // 关键：complete 之前解析业务实例（完成后引擎实例可能已离开运行表）
        ProcessInstance instance = instanceByTask(task);
        String nodeName = nodeName(instance, task);

        Map<String, Object> vars = req.getFormData() == null ? Map.of() : new HashMap<>(req.getFormData());
        taskService.complete(req.getTaskId(), vars);

        Map<String, Object> merged = parseJsonObject(instance.getFormData());
        merged.putAll(vars);
        instance.setFormData(toJson(merged));
        instanceMapper.updateById(instance);

        record(instance.getId(), "submit", task.getTaskDefinitionKey(), nodeName,
                task.getAssignee(), null, task.getId());
    }

    // ============ 审批 ============

    /**
     * 审批：approved 变量驱动 BPMN 批准/驳回线（驳回线按节点策略接好目标）。
     * 意见写业务轨迹表 process_record。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(ApproveRequest req) {
        Task task = requireTask(req.getTaskId());
        // 关键：complete 之前解析业务实例（terminate/结束流程后实例已离开引擎运行表）
        ProcessInstance instance = instanceByTask(task);
        String nodeName = nodeName(instance, task);

        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", req.isApproved());
        taskService.complete(req.getTaskId(), vars);

        record(instance.getId(), req.isApproved() ? "approve" : "reject",
                task.getTaskDefinitionKey(), nodeName,
                task.getAssignee(), req.getComment(), task.getId());
    }

    // ============ 查询 ============

    /** 待办列表（引擎任务 + 业务实例上下文） */
    public List<TaskViewDto> myTasks(String assignee) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc()
                .list();
        List<TaskViewDto> result = new ArrayList<>();
        for (Task t : tasks) {
            String bizKey = businessKeyOf(t);
            if (bizKey == null) continue;
            ProcessInstance instance;
            try {
                instance = instanceMapper.selectById(Long.valueOf(bizKey));
            } catch (Exception e) {
                continue;
            }
            if (instance == null) continue;

            ProcessNode node = findNode(instance.getProcessDefId(), t.getTaskDefinitionKey());
            TaskViewDto dto = new TaskViewDto();
            dto.setTaskId(t.getId());
            dto.setInstanceId(instance.getId());
            dto.setBizNo(instance.getBizNo());
            dto.setTitle(instance.getTitle());
            dto.setNodeKey(t.getTaskDefinitionKey());
            dto.setNodeName(node != null ? node.getNodeName() : t.getTaskDefinitionKey());
            dto.setNodeType(node != null ? node.getNodeType() : null);
            Date createTime = t.getCreateTime();
            dto.setCreateTime(createTime);
            result.add(dto);
        }
        return result;
    }

    /** 实例详情（业务单据） */
    public ProcessInstance getInstance(Long id) {
        ProcessInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException("实例不存在：id=" + id);
        }
        return instance;
    }

    /** 实例轨迹（审批意见/操作记录） */
    public List<ProcessRecord> records(Long instanceId) {
        return recordMapper.selectList(
                new LambdaQueryWrapper<ProcessRecord>()
                        .eq(ProcessRecord::getProcessInstanceId, instanceId)
                        .orderByAsc(ProcessRecord::getId));
    }

    /**
     * 获取实例「当前节点」的表单 schema（前端渲染用）。
     * 引擎当前任务的 taskDefinitionKey = nodeKey → 快照直接取字段；已结束则返回 finished 标记。
     */
    public Map<String, Object> getNodeSchema(Long instanceId) {
        ProcessInstance instance = getInstance(instanceId);
        if (instance.getFlowableInstanceId() == null) {
            throw new BizException("实例未绑定引擎，无法获取节点");
        }
        FormSchemaSnapshot snapshot = snapshotMapper.selectById(instance.getFormSchemaSnapshotId());
        if (snapshot == null) {
            throw new BizException("快照不存在");
        }
        Map<String, Object> root = parseJsonObject(snapshot.getSnapshotJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> nodes = (Map<String, Object>) root.get("nodes");
        Map<String, Object> formData = parseJsonObject(instance.getFormData());

        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .active()
                .singleResult();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", root.get("schemaVersion"));
        result.put("formData", formData);

        if (task == null) {
            // 流程已结束（完成或终止）
            HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instance.getFlowableInstanceId())
                    .singleResult();
            result.put("nodeKey", "end");
            result.put("nodeType", "end");
            result.put("finished", true);
            result.put("endActivityId", hi != null ? hi.getEndActivityId() : null);
            return result;
        }

        String nodeKey = task.getTaskDefinitionKey();
        ProcessNode node = findNode(instance.getProcessDefId(), nodeKey);
        result.put("taskId", task.getId());
        result.put("nodeKey", nodeKey);
        result.put("nodeName", node != null ? node.getNodeName() : nodeKey);
        result.put("nodeType", node != null ? node.getNodeType() : null);
        result.put("finished", false);
        result.put("fields", nodes != null ? nodes.get(nodeKey) : null);
        return result;
    }

    // ============ 内部工具 ============

    private Task requireTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException("任务不存在或已处理：id=" + taskId);
        }
        return task;
    }

    private ProcessInstance instanceByTask(Task task) {
        String bizKey = businessKeyOf(task);
        if (bizKey == null) {
            throw new BizException("任务缺少业务实例映射（businessKey）");
        }
        try {
            Long instanceId = Long.valueOf(bizKey);
            ProcessInstance instance = instanceMapper.selectById(instanceId);
            if (instance == null) {
                throw new BizException("任务所属业务实例不存在");
            }
            return instance;
        } catch (NumberFormatException e) {
            throw new BizException("任务业务实例映射非法（businessKey）");
        }
    }

    /** Flowable 8 的 Task 接口移除了 getProcessInstanceBusinessKey，改从运行时实例查询 */
    private String businessKeyOf(Task task) {
        org.flowable.engine.runtime.ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }

    private ProcessNode findNode(Long defId, String nodeKey) {
        return nodeMapper.selectOne(
                new LambdaQueryWrapper<ProcessNode>()
                        .eq(ProcessNode::getProcessDefId, defId)
                        .eq(ProcessNode::getNodeKey, nodeKey));
    }

    private String nodeName(ProcessInstance instance, Task task) {
        ProcessNode node = findNode(instance.getProcessDefId(), task.getTaskDefinitionKey());
        return node != null ? node.getNodeName() : task.getTaskDefinitionKey();
    }

    /** 写流程轨迹 */
    private void record(Long instanceId, String action, String nodeKey, String nodeName,
                        String executor, String comment, String flowableTaskId) {
        ProcessRecord r = new ProcessRecord();
        r.setProcessInstanceId(instanceId);
        r.setNodeKey(nodeKey);
        r.setNodeName(nodeName);
        r.setActionType(action);
        r.setExecutor(executor);
        r.setComment(comment);
        r.setFlowableTaskId(flowableTaskId);
        recordMapper.insert(r);
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("JSON 解析失败：" + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败：" + e.getMessage());
        }
    }
}
