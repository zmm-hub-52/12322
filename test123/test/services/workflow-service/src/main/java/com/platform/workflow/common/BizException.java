package com.platform.workflow.common;

import lombok.Getter;

/**
 * 业务异常（带状态码）。
 * 由 GlobalExceptionHandler 统一捕获并转换为 Result 响应。
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
