package com.platform.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工作流 + 动态数据表单微服务启动类。
 *
 * 职责：
 * 1. 流程/节点/节点字段 的配置管理（管理员配置化构建审批表单）
 * 2. 字段库与维度维表维护（动态表单的字段与可选值）
 * 3. 流程发起、实例流转、任务审批（Flowable 引擎）
 */
@SpringBootApplication
@MapperScan("com.platform.workflow.mapper")
public class WorkflowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
