package com.platform.workflow.dto;

import com.platform.workflow.entity.ProcessNode;
import lombok.Data;

import java.util.List;

/** 流程整图（节点 + 连线），前端画布与后端配置的交换结构 */
@Data
public class GraphDto {

    private List<ProcessNode> nodes;

    private List<LinkDto> links;
}
