package com.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.workflow.common.BizException;
import com.platform.workflow.dto.ConditionDto;
import com.platform.workflow.dto.GraphDto;
import com.platform.workflow.dto.LinkDto;
import com.platform.workflow.entity.FormSchemaSnapshot;
import com.platform.workflow.entity.NodeFormField;
import com.platform.workflow.entity.ProcessDef;
import com.platform.workflow.entity.ProcessLink;
import com.platform.workflow.entity.ProcessNode;
import com.platform.workflow.mapper.FormSchemaSnapshotMapper;
import com.platform.workflow.mapper.NodeFormFieldMapper;
import com.platform.workflow.mapper.ProcessDefMapper;
import com.platform.workflow.mapper.ProcessLinkMapper;
import com.platform.workflow.mapper.ProcessNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程配置服务（v2）：流程定义 / 图（节点+连线） / 节点字段 的配置管理 + 发布。
 *
 * 发布机制（编译 + 部署）：
 * 1. 版本 +1（首次发布 version 不变）；
 * 2. SchemaAssembler 生成该版本的表单快照（同版本实例共享，运行期只读）；
 * 3. BpmnGenerator 把「节点+连线」编译成 BPMN 2.0 XML；
 * 4. 经 RepositoryService 部署给 Flowable（key=defCode，每版本一次部署），deploymentId 记回流程定义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessConfigService {

    private final ProcessDefMapper defMapper;
    private final ProcessNodeMapper nodeMapper;
    private final ProcessLinkMapper linkMapper;
    private final NodeFormFieldMapper nodeFormFieldMapper;
    private final FormSchemaSnapshotMapper snapshotMapper;
    private final SchemaAssembler schemaAssembler;
    private final BpmnGenerator bpmnGenerator;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    // ============ 流程定义 ============

    /** 查询全部流程（含草稿与已发布） */
    public List<ProcessDef> listDefs() {
        return defMapper.selectList(
                new LambdaQueryWrapper<ProcessDef>().orderByAsc(ProcessDef::getId));
    }

    public ProcessDef getDef(Long id) {
        ProcessDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BizException("流程不存在：id=" + id);
        }
        return def;
    }

    /** 新增流程定义（version=1 草稿） */
    public ProcessDef createDef(ProcessDef def) {
        validateCode(def.getCode());
        def.setId(null);
        def.setVersion(1);
        def.setStatus("draft");
        defMapper.insert(def);
        return def;
    }

    public ProcessDef updateDef(Long id, ProcessDef def) {
        getDef(id);
        def.setId(id);
        defMapper.updateById(def);
        return def;
    }

    /** 流程编码合法性（= BPMN process id/key，须 NCName：字母/下划线开头） */
    private static final java.util.regex.Pattern CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*$");

    private void validateCode(String code) {
        if (code == null || code.isBlank() || !CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new BizException("流程编码需以字母/下划线开头，只含字母数字下划线（BPMN 约束），当前：" + code);
        }
    }

    public void deleteDef(Long id) {
        getDef(id);
        defMapper.deleteById(id); // 逻辑删除
    }

    // ============ 图（节点 + 连线） ============

    /** 读取整图：{ nodes, links }（连线以 nodeKey 表达） */
    public GraphDto getGraph(Long defId) {
        getDef(defId);
        List<ProcessNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ProcessNode>()
                        .eq(ProcessNode::getProcessDefId, defId)
                        .orderByAsc(ProcessNode::getId));
        List<ProcessLink> links = linkMapper.selectList(
                new LambdaQueryWrapper<ProcessLink>()
                        .eq(ProcessLink::getProcessDefId, defId)
                        .orderByAsc(ProcessLink::getSortNo));

        Map<Long, String> idToKey = nodes.stream()
                .collect(Collectors.toMap(ProcessNode::getId, ProcessNode::getNodeKey));

        GraphDto dto = new GraphDto();
        dto.setNodes(nodes);
        dto.setLinks(links.stream().map(l -> {
            LinkDto ld = new LinkDto();
            ld.setId(l.getId());
            ld.setFromKey(idToKey.get(l.getFromNodeId()));
            ld.setToKey(idToKey.get(l.getToNodeId()));
            ld.setCondition(parseCondition(l.getConditionJson()));
            ld.setSortNo(l.getSortNo());
            return ld;
        }).collect(Collectors.toList()));
        return dto;
    }

    /**
     * 保存整图（草稿）：
     * 节点按 nodeKey 匹配更新/新增，图中不存在的软删；连线全量重建。
     * 说明：软删节点时其 node_form_field 配置保留（将来同 key 重建可恢复引用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public GraphDto saveGraph(Long defId, GraphDto dto) {
        getDef(defId);
        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new BizException("图不能为空");
        }

        // 1) 节点 upsert（按 nodeKey）
        Map<String, ProcessNode> existingByKey = nodeMapper.selectList(
                        new LambdaQueryWrapper<ProcessNode>().eq(ProcessNode::getProcessDefId, defId))
                .stream().collect(Collectors.toMap(ProcessNode::getNodeKey, n -> n, (a, b) -> a));

        Map<String, ProcessNode> newByKey = new HashMap<>();
        for (ProcessNode n : dto.getNodes()) {
            n.setProcessDefId(defId);
            if (n.getRejectStrategy() == null) {
                n.setRejectStrategy("back_prev");
            }
            ProcessNode old = existingByKey.get(n.getNodeKey());
            if (old != null) {
                n.setId(old.getId());
                nodeMapper.updateById(n);
            } else {
                n.setId(null);
                nodeMapper.insert(n);
            }
            newByKey.put(n.getNodeKey(), n);
        }
        for (ProcessNode old : existingByKey.values()) {
            if (!newByKey.containsKey(old.getNodeKey())) {
                nodeMapper.deleteById(old.getId()); // 逻辑删除
            }
        }

        // 2) 连线全量重建
        linkMapper.delete(new LambdaQueryWrapper<ProcessLink>().eq(ProcessLink::getProcessDefId, defId));
        if (dto.getLinks() != null) {
            int i = 0;
            for (LinkDto l : dto.getLinks()) {
                ProcessNode from = newByKey.get(l.getFromKey());
                ProcessNode to = newByKey.get(l.getToKey());
                if (from == null || to == null) {
                    throw new BizException("连线引用了不存在的节点：" + l.getFromKey() + " -> " + l.getToKey());
                }
                ProcessLink link = new ProcessLink();
                link.setProcessDefId(defId);
                link.setFromNodeId(from.getId());
                link.setToNodeId(to.getId());
                link.setConditionJson(toJson(l.getCondition()));
                link.setSortNo(l.getSortNo() != null ? l.getSortNo() : i);
                linkMapper.insert(link);
                i++;
            }
        }
        log.info("[saveGraph] 流程 {} 图已保存：{} 节点 / {} 连线", defId, newByKey.size(), dto.getLinks() == null ? 0 : dto.getLinks().size());
        return getGraph(defId);
    }

    // ============ 节点字段（schema 实体） ============

    /** 查询某节点的字段配置（按顺序） */
    public List<NodeFormField> listNodeFields(Long nodeId) {
        return nodeFormFieldMapper.selectList(
                new LambdaQueryWrapper<NodeFormField>()
                        .eq(NodeFormField::getNodeId, nodeId)
                        .orderByAsc(NodeFormField::getOrderNo));
    }

    public NodeFormField createNodeField(Long nodeId, NodeFormField field) {
        if (nodeMapper.selectById(nodeId) == null) {
            throw new BizException("节点不存在：id=" + nodeId);
        }
        field.setId(null);
        field.setNodeId(nodeId);
        nodeFormFieldMapper.insert(field);
        return field;
    }

    public NodeFormField updateNodeField(Long id, NodeFormField field) {
        if (nodeFormFieldMapper.selectById(id) == null) {
            throw new BizException("节点字段不存在：id=" + id);
        }
        field.setId(id);
        nodeFormFieldMapper.updateById(field);
        return field;
    }

    public void deleteNodeField(Long id) {
        if (nodeFormFieldMapper.selectById(id) == null) {
            throw new BizException("节点字段不存在：id=" + id);
        }
        nodeFormFieldMapper.deleteById(id);
    }

    // ============ 发布（快照 + BPMN 编译部署） ============

    /**
     * 发布流程：
     * 1. 校验有节点；fill 节点必须有字段（与前端发布校验一致）
     * 2. 已发布则 version +1（首次发布 version 不变）
     * 3. 生成该版本表单快照
     * 4. BpmnGenerator 编译 BPMN XML → RepositoryService 部署（每版本一次部署）
     * 5. 更新流程状态 published + deploymentId
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessDef publish(Long defId) {
        ProcessDef def = getDef(defId);

        List<ProcessNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ProcessNode>()
                        .eq(ProcessNode::getProcessDefId, defId)
                        .orderByAsc(ProcessNode::getId));
        if (nodes.isEmpty()) {
            throw new BizException("流程没有节点，无法发布");
        }
        // 发布校验：fill 节点必须配置字段
        List<String> emptyFills = new ArrayList<>();
        for (ProcessNode n : nodes) {
            if ("fill".equals(n.getNodeType()) && listNodeFields(n.getId()).isEmpty()) {
                emptyFills.add(n.getNodeName());
            }
        }
        if (!emptyFills.isEmpty()) {
            throw new BizException("以下填写节点尚未配置字段，无法发布：" + String.join("、", emptyFills));
        }

        int newVersion = "published".equals(def.getStatus()) ? def.getVersion() + 1 : def.getVersion();

        // 快照
        String schema = schemaAssembler.assembleFullSchema(defId);
        FormSchemaSnapshot snapshot = new FormSchemaSnapshot();
        snapshot.setProcessDefId(defId);
        snapshot.setProcessDefVersion(newVersion);
        snapshot.setSnapshotJson(schema);
        snapshotMapper.insert(snapshot);

        // BPMN 编译 + 部署
        List<ProcessLink> links = linkMapper.selectList(
                new LambdaQueryWrapper<ProcessLink>().eq(ProcessLink::getProcessDefId, defId));
        String bpmnXml = bpmnGenerator.generate(def, nodes, links);
        // 注入版本注释（XML 声明之后）：确保每次发布资源内容不同，绕过 Flowable 部署重复过滤
        bpmnXml = bpmnXml.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- workflow-service deploy v" + newVersion + " -->\n");
        Deployment deployment = repositoryService.createDeployment()
                .key(def.getCode())
                .name(def.getName() + " v" + newVersion)
                .addString(def.getCode() + ".bpmn20.xml", bpmnXml)
                .deploy();

        // 更新流程定义
        def.setVersion(newVersion);
        def.setStatus("published");
        def.setDeploymentId(deployment.getId());
        defMapper.updateById(def);

        log.info("[publish] 流程 {} v{} 已发布：快照 id={}，部署 id={}", def.getCode(), newVersion, snapshot.getId(), deployment.getId());
        return def;
    }

    /**
     * 获取最新发布版本的表单快照（发起流程时预览表单用）。
     * 返回快照 JSON 对象：{ schemaVersion, nodes: { nodeKey: [fields...] } }。
     */
    public Map<String, Object> getLatestSchema(Long defId) {
        getDef(defId);
        FormSchemaSnapshot snapshot = snapshotMapper.selectList(
                        new LambdaQueryWrapper<FormSchemaSnapshot>()
                                .eq(FormSchemaSnapshot::getProcessDefId, defId)
                                .orderByDesc(FormSchemaSnapshot::getProcessDefVersion))
                .stream().findFirst().orElse(null);
        if (snapshot == null) {
            throw new BizException("流程尚未发布，无表单快照");
        }
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("快照解析失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private ConditionDto parseCondition(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<ConditionDto>() {});
        } catch (Exception e) {
            throw new BizException("条件 JSON 解析失败：" + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败：" + e.getMessage());
        }
    }
}
