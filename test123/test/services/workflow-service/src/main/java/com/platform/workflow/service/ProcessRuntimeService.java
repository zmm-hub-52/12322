package com.platform.workflow.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.dto.ApproveRequest;
import com.platform.workflow.dto.FinishedInstanceDto;
import com.platform.workflow.dto.StartRequest;
import com.platform.workflow.dto.SubmitRequest;
import com.platform.workflow.dto.TaskViewDto;
import com.platform.workflow.entity.FormSchemaSnapshot;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.entity.ProcessInstance;
import com.platform.workflow.entity.ProcessRecord;
import com.platform.workflow.mapper.FormSchemaSnapshotMapper;
import com.platform.workflow.mapper.ProcessDefMapper;
import com.platform.workflow.mapper.ProcessInstanceMapper;
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

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        instance.setStatus("active");
        instanceMapper.insert(instance);

        // 流程变量（字段值直接作变量，供排他条件表达式使用）
        Map<String, Object> vars = new HashMap<>();
        vars.put("applicant", req.getApplicant());
        vars.put("bizNo", req.getBizNo());
        vars.put("title", req.getTitle());
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
        }
        // 只分配「发起后的第一个用户任务」处理人（固定自动；动态必须由发起人指定）
        Map<String, Object> graph = parseGraph(snapshot.getGraphJson());
        for (Map<String, Object> n : firstUserTasks(graph)) {
            vars.put("assignee_" + gKey(n), resolveAssignee(n, req.getAssignees()));
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

        // 表单数据变量（作为流程变量供条件路由）
        Map<String, Object> formVars = req.getFormData() == null ? new HashMap<>() : new HashMap<>(req.getFormData());
        // 下一处理人变量（固定自动；动态由上一人指定；不并入业务表单数据）
        Map<String, Object> assigneeVars = new HashMap<>();
        Map<String, Object> graph = graphOf(instance);
        for (Map<String, Object> next : nextUserTasks(graph, task.getTaskDefinitionKey())) {
            assigneeVars.put("assignee_" + gKey(next), resolveAssignee(next, req.getNextAssignees()));
        }
        Map<String, Object> completeVars = new HashMap<>(formVars);
        completeVars.putAll(assigneeVars);
        taskService.complete(req.getTaskId(), completeVars);

        Map<String, Object> merged = parseJsonObject(instance.getFormData());
        merged.putAll(formVars);
        instance.setFormData(toJson(merged));
        instanceMapper.updateById(instance);
        // 流程可能已结束：同步更新状态与结束时间
        updateStatusIfEnded(instance);

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

        // 审批节点可编辑字段的表单数据（作为流程变量并合并进实例数据）
        Map<String, Object> formVars = req.getFormData() == null ? new HashMap<>() : new HashMap<>(req.getFormData());
        Map<String, Object> completeVars = new HashMap<>(formVars);
        completeVars.put("approved", req.isApproved());
        // 下一处理人（仅批准线；驳回线按策略回退到已定处理人）
        if (req.isApproved()) {
            Map<String, Object> graph = graphOf(instance);
            for (Map<String, Object> next : nextUserTasks(graph, task.getTaskDefinitionKey())) {
                completeVars.put("assignee_" + gKey(next), resolveAssignee(next, req.getNextAssignees()));
            }
        }
        taskService.complete(req.getTaskId(), completeVars);

        // 合并审批人修改到实例数据
        if (!formVars.isEmpty()) {
            Map<String, Object> merged = parseJsonObject(instance.getFormData());
            merged.putAll(formVars);
            instance.setFormData(toJson(merged));
            instanceMapper.updateById(instance);
        }
        // 流程可能已结束：同步更新状态与结束时间
        updateStatusIfEnded(instance);

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

            Map<String, Object> node = null;
            try {
                node = findNode(graphOf(instance), t.getTaskDefinitionKey());
            } catch (BizException ignored) {
                // 旧快照缺 graph_json：降级为仅显示 taskDefinitionKey
            }
            TaskViewDto dto = new TaskViewDto();
            dto.setTaskId(t.getId());
            dto.setInstanceId(instance.getId());
            dto.setBizNo(instance.getBizNo());
            dto.setTitle(instance.getTitle());
            dto.setNodeKey(t.getTaskDefinitionKey());
            dto.setNodeName(node != null ? gName(node) : t.getTaskDefinitionKey());
            dto.setNodeType(node != null ? gType(node) : null);
            dto.setCreateTime(t.getCreateTime() == null ? null
                    : t.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
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

    /** 已完结实例列表（正常走完 + 驳回终止） */
    public List<FinishedInstanceDto> listFinished() {
        List<ProcessInstance> list = instanceMapper.selectList(
                new LambdaQueryWrapper<ProcessInstance>()
                        .in(ProcessInstance::getStatus, "finished", "terminated")
                        .orderByDesc(ProcessInstance::getId));
        List<FinishedInstanceDto> result = new ArrayList<>();
        for (ProcessInstance i : list) {
            ProcessDef def = i.getProcessDefId() == null ? null : defMapper.selectById(i.getProcessDefId());
            FinishedInstanceDto dto = new FinishedInstanceDto();
            dto.setId(i.getId());
            dto.setBizNo(i.getBizNo());
            dto.setTitle(i.getTitle());
            dto.setApplicant(i.getApplicant());
            dto.setProcessName(def != null ? def.getName() : null);
            dto.setCreateTime(i.getCreateTime());
            dto.setEndTime(i.getEndTime());
            dto.setStatus(i.getStatus());
            result.add(dto);
        }
        return result;
    }

    /** 导出单个实例的 formData（一行，列头=字段英文 key） */
    public byte[] exportFormData(Long instanceId) {
        ProcessInstance instance = getInstance(instanceId);
        Map<String, Object> formData = parseJsonObject(instance.getFormData());
        List<String> keys = new ArrayList<>(formData.keySet());
        List<List<String>> head = new ArrayList<>();
        for (String key : keys) {
            head.add(List.of(key));
        }
        List<Object> row = new ArrayList<>();
        for (String key : keys) {
            row.add(formData.get(key));
        }
        List<List<Object>> data = new ArrayList<>();
        data.add(row);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("form_data").doWrite(data);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 获取实例「当前节点」的表单 schema（前端渲染用）。
     * 引擎当前任务的 taskDefinitionKey = nodeKey → 快照直接取字段；已结束则返回 finished 标记。
     */
    public Map<String, Object> getNodeSchema(Long instanceId, String taskId) {
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

        // 按 taskId 定位具体任务（并行时一个实例有多个活动任务，不能对整个实例 singleResult）
        Task task = null;
        if (taskId != null && !taskId.isBlank()) {
            task = taskService.createTaskQuery().taskId(taskId).singleResult();
        }

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
        Map<String, Object> graph = parseGraph(snapshot.getGraphJson());
        Map<String, Object> node = findNode(graph, nodeKey);
        result.put("taskId", task.getId());
        result.put("nodeKey", nodeKey);
        result.put("nodeName", node != null ? gName(node) : nodeKey);
        result.put("nodeType", node != null ? gType(node) : null);
        result.put("finished", false);
        result.put("fields", nodes != null ? nodes.get(nodeKey) : null);
        // 下一用户任务列表（供「下一处理人」选择；串行 1 个，并行多支）
        List<Map<String, Object>> nextNodes = new ArrayList<>();
        for (Map<String, Object> next : nextUserTasks(graph, nodeKey)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeKey", gKey(next));
            m.put("nodeName", gName(next));
            m.put("nodeType", gType(next));
            m.put("assigneeMode", gAssigneeMode(next) == null ? "dynamic" : gAssigneeMode(next));
            m.put("fixedAssignee", gFixedAssignee(next));
            nextNodes.add(m);
        }
        if (!nextNodes.isEmpty()) {
            result.put("nextNodes", nextNodes);
        }
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

    /** 解析某实例的可执行图快照（节点 + 连线），失败抛异常提示重新发布 */
    private Map<String, Object> graphOf(ProcessInstance instance) {
        FormSchemaSnapshot snapshot = snapshotMapper.selectById(instance.getFormSchemaSnapshotId());
        if (snapshot == null) {
            throw new BizException("流程快照不存在，请重新发布");
        }
        return parseGraph(snapshot.getGraphJson());
    }

    private Map<String, Object> parseGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            throw new BizException("流程缺少可执行图快照，请重新发布该流程");
        }
        try {
            return objectMapper.readValue(graphJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("图快照解析失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphNodeList(Map<String, Object> graph) {
        return (List<Map<String, Object>>) graph.get("nodes");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphLinkList(Map<String, Object> graph) {
        return (List<Map<String, Object>>) graph.get("links");
    }

    /** 从图快照中按 key 找节点（找不到返回 null） */
    private Map<String, Object> findNode(Map<String, Object> graph, String key) {
        for (Map<String, Object> n : graphNodeList(graph)) {
            if (key.equals(gKey(n))) return n;
        }
        return null;
    }

    /** 发起后的「第一个用户任务」节点（串行 1 个，并行多支） */
    private List<Map<String, Object>> firstUserTasks(Map<String, Object> graph) {
        Map<String, Object> start = graphNodeList(graph).stream()
                .filter(n -> "start".equals(gType(n))).findFirst().orElse(null);
        if (start == null) return List.of();
        return collectUserTasks(graph, gKey(start));
    }

    /** 当前节点的「下一个用户任务」节点（串行 1 个，并行多支） */
    private List<Map<String, Object>> nextUserTasks(Map<String, Object> graph, String fromKey) {
        return collectUserTasks(graph, fromKey);
    }

    /** BFS：从 fromKey 出发，沿连线跨过网关收集「第一层」用户任务（fill/approval） */
    private List<Map<String, Object>> collectUserTasks(Map<String, Object> graph, String fromKey) {
        List<Map<String, Object>> nodes = graphNodeList(graph);
        List<Map<String, Object>> links = graphLinkList(graph);
        Map<String, Map<String, Object>> byKey = new HashMap<>();
        for (Map<String, Object> n : nodes) byKey.put(gKey(n), n);
        Map<String, List<String>> out = new HashMap<>();
        for (Map<String, Object> l : links) {
            out.computeIfAbsent(gFrom(l), k -> new ArrayList<>()).add(gTo(l));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(fromKey);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!visited.add(cur)) continue;
            for (String toKey : out.getOrDefault(cur, List.of())) {
                Map<String, Object> next = byKey.get(toKey);
                if (next == null) continue;
                String t = gType(next);
                if ("fill".equals(t) || "approval".equals(t)) {
                    if (result.stream().noneMatch(x -> gKey(x).equals(gKey(next)))) {
                        result.add(next);
                    }
                } else if ("parallelSplit".equals(t) || "exclusiveSplit".equals(t)
                        || "parallelJoin".equals(t) || "exclusiveJoin".equals(t)) {
                    queue.add(gKey(next));
                }
            }
        }
        return result;
    }

    /** 解析节点处理人：固定返回 fixed_assignee；动态必须由 assignees 提供 */
    private String resolveAssignee(Map<String, Object> n, Map<String, String> assignees) {
        String mode = gAssigneeMode(n);
        String fixed = gFixedAssignee(n);
        if ("fixed".equals(mode) && fixed != null && !fixed.isBlank()) {
            return fixed;
        }
        String user = assignees != null ? assignees.get(gKey(n)) : null;
        if (user == null || user.isBlank()) {
            throw new BizException("请选择处理人：" + gName(n));
        }
        return user;
    }

    private String nodeName(ProcessInstance instance, Task task) {
        Map<String, Object> node = findNode(graphOf(instance), task.getTaskDefinitionKey());
        return node != null ? gName(node) : task.getTaskDefinitionKey();
    }

    // ---- 图字段访问器（统一 String 化，避免 Jackson 数值类型差异） ----
    private static String gKey(Map<String, Object> n) { return str(n.get("key")); }
    private static String gName(Map<String, Object> n) { return str(n.get("name")); }
    private static String gType(Map<String, Object> n) { return str(n.get("type")); }
    private static String gAssigneeMode(Map<String, Object> n) { return str(n.get("assigneeMode")); }
    private static String gFixedAssignee(Map<String, Object> n) { return str(n.get("fixedAssignee")); }
    private static String gFrom(Map<String, Object> l) { return str(l.get("fromKey")); }
    private static String gTo(Map<String, Object> l) { return str(l.get("toKey")); }
    private static String str(Object v) { return v == null ? null : String.valueOf(v); }

    /** 提交/审批完成后：若流程已结束，同步更新业务实例状态与结束时间 */
    private void updateStatusIfEnded(ProcessInstance instance) {
        if (instance.getFlowableInstanceId() == null) {
            return;
        }
        org.flowable.engine.runtime.ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId()).singleResult();
        if (pi != null) {
            return; // 仍在运行
        }
        HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId()).singleResult();
        boolean terminated = hi != null && hi.getEndActivityId() != null
                && hi.getEndActivityId().contains("rejEnd");
        instance.setStatus(terminated ? "terminated" : "finished");
        if (hi != null && hi.getEndTime() != null) {
            instance.setEndTime(hi.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        instanceMapper.updateById(instance);
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
