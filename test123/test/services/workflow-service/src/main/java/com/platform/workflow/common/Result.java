package com.platform.workflow.common;

import lombok.Data;

/**
 * 统一响应结构。
 * code = 0 表示成功，非 0 表示失败。
 */
@Data
public class Result<T> {

    /** 状态码：0 成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 数据 */
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String message) {
        return fail(400, message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
