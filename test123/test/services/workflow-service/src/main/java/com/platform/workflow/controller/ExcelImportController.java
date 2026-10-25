package com.platform.workflow.controller;

import com.platform.workflow.common.Result;
import com.platform.workflow.dto.ExcelBatchStartRequest;
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
import java.util.Map;

/**
 * Excel 导入发起接口：下载模板 -> 上传预览校验 -> 批量发起（一个流程实例 = 一张表 = N 行）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;
    private final ProcessRuntimeService runtimeService;

    @GetMapping("/process/{id}/excel-template")
    public ResponseEntity<byte[]> template(@PathVariable Long id) {
        byte[] bytes = excelImportService.buildTemplate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=flow-template-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/instance/excel/preview")
    public Result<ImportPreviewDto> preview(
            @RequestParam("defId") Long defId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传 Excel 文件");
        }
        return Result.ok(excelImportService.parseAndValidate(defId, file));
    }

    @PostMapping("/instance/excel/start-batch")
    public Result<ProcessInstance> startBatch(@RequestBody ExcelBatchStartRequest req) {
        if (req.getRows() == null || req.getRows().isEmpty()) {
            return Result.fail("明细行不能为空");
        }
        java.util.List<Map<String, Object>> converted = excelImportService.convertRows(req.getProcessDefId(), req.getRows());
        StartRequest startReq = new StartRequest();
        startReq.setProcessDefId(req.getProcessDefId());
        startReq.setApplicant(req.getApplicant());
        startReq.setBizNo(req.getBizNo());
        startReq.setTitle(req.getTitle());
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("rows", converted);
        startReq.setFormData(formData);
        Map<String, String> assignees = new HashMap<>();
        if (req.getAssignees() != null) {
            assignees.putAll(req.getAssignees());
        }
        startReq.setAssignees(assignees);
        return Result.ok(runtimeService.start(startReq));
    }
}
