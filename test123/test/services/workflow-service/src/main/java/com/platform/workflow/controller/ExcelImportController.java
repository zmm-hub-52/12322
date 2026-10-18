package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.dto.ExcelStartRequest;
import com.platform.workflow.dto.ImportPreviewDto;
import com.platform.workflow.dto.StartRequest;
import com.platform.workflow.entity.ProcessInstance;
import com.platform.workflow.service.ExcelImportService;
import com.platform.workflow.service.ProcessRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入发起接口（两步：下载模板 → 上传预览校验 → 逐行确认发起）。
 * 模板列 = start 节点配置的字段（表头 = 字段 key 一行）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;
    private final ProcessRuntimeService runtimeService;

    /**
     * 接口：GET /api/process/{id}/excel-template —— 下载 Excel 模板
     * 表头 = start 节点字段 key（一行）；列顺序 = 配置顺序。
     */
    @GetMapping("/process/{id}/excel-template")
    public ResponseEntity<byte[]> template(@PathVariable Long id) {
        byte[] bytes = excelImportService.buildTemplate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=flow-template-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /**
     * 接口：POST /api/instance/excel/preview —— 上传解析 + 校验（不发起）
     * 参数：multipart：file Excel、defId 流程ID。
     * 返回：{ keys 列定义, rows: [{rowNo, data, valid, errors}] }。
     */
    @PostMapping("/instance/excel/preview")
    public Result<ImportPreviewDto> preview(
            @RequestParam Long defId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传 Excel 文件");
        }
        return Result.ok(excelImportService.parseAndValidate(defId, file));
    }

    /**
     * 接口：POST /api/instance/excel/start —— 预览通过的行逐条发起
     * 参数：ExcelStartRequest（processDefId、data 该行数据、applicant、assignees、bizNo、title）。
     * 说明：后端按 start 字段类型转换 data 值（number→数字），保证流程条件路由正确。
     * 返回：发起的业务实例。
     */
    @PostMapping("/instance/excel/start")
    public Result<ProcessInstance> start(@RequestBody ExcelStartRequest req) {
        // 按 start 字段类型转换值（Excel 全字符串）
        List<Map<String, Object>> fields = excelImportService.getStartFields(req.getProcessDefId());
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : (req.getData() == null ? Map.<String, Object>of() : req.getData()).entrySet()) {
            Map<String, Object> field = fields.stream()
                    .filter(f -> e.getKey().equals(String.valueOf(f.get("key"))))
                    .findFirst().orElse(null);
            String type = field == null ? "input" : String.valueOf(field.get("type"));
            Object v = e.getValue();
            if (v != null && "number".equals(type) && v instanceof String s) {
                try {
                    converted.put(e.getKey(), Double.parseDouble(s));
                } catch (NumberFormatException ex) {
                    converted.put(e.getKey(), s);
                }
            } else if (v != null && "boolean".equals(type) && v instanceof String s) {
                converted.put(e.getKey(), "true".equalsIgnoreCase(s) || "是".equals(s));
            } else {
                converted.put(e.getKey(), v);
            }
        }

        StartRequest startReq = new StartRequest();
        startReq.setProcessDefId(req.getProcessDefId());
        startReq.setApplicant(req.getApplicant());
        startReq.setBizNo(req.getBizNo());
        startReq.setTitle(req.getTitle());
        startReq.setFormData(converted);
        Map<String, String> assignees = new HashMap<>();
        if (req.getAssignees() != null) {
            assignees.putAll(req.getAssignees());
        }
        startReq.setAssignees(assignees);
        return Result.ok(runtimeService.start(startReq));
    }
}
