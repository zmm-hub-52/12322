package com.platform.workflow.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转换为 Result 响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("[biz] {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String msg = error != null ? error.getDefaultMessage() : "参数校验失败";
        return Result.fail(400, msg);
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("[error]", e);
        return Result.fail(500, "系统异常：" + e.getMessage());
    }
}
