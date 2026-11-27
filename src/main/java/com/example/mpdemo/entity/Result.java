package com.example.mpdemo.entity;

import lombok.Data;

/**
 * 统一 API 响应封装
 */
@Data
public class Result {
    private Integer code;
    private String message;
    private Object data;

    public Result() {
    }

    public Result(Integer code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success(String message, Object data) {
        return new Result(200, message, data);
    }

    public static Result success(Object data) {
        return new Result(200, "操作成功", data);
    }

    /**
     * 业务错误（默认 400 Bad Request）
     */
    public static Result error(String message) {
        return new Result(400, message, null);
    }

    /**
     * 指定状态码的业务错误
     */
    public static Result error(Integer code, String message) {
        return new Result(code, message, null);
    }
}
