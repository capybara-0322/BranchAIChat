package com.example.ai.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结果类
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    
    private Integer code;
    private String msg;
    private T data;
    
    // 构造函数
    public Result() {}
    
    public Result(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
    
    // 成功响应
    public static <T> Result<T> success() {
        return new Result<>(0, "成功");
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "成功", data);
    }
    
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(0, msg, data);
    }
    
    // 失败响应
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg);
    }
    
    public static <T> Result<T> error(String msg) {
        return new Result<>(1001, msg);
    }
    
    // 常用错误码
    public static <T> Result<T> paramError(String msg) {
        return new Result<>(1001, msg);
    }
    
    public static <T> Result<T> loginFailed() {
        return new Result<>(1002, "登录失败，用户名或密码错误");
    }
    
    public static <T> Result<T> unauthorized() {
        return new Result<>(1003, "未授权，Token缺失或无效");
    }
    
    public static <T> Result<T> tokenExpired() {
        return new Result<>(1004, "Token过期，需重新登录");
    }
    
    public static <T> Result<T> usernameExists() {
        return new Result<>(1005, "用户名已存在");
    }
    
    public static <T> Result<T> passwordWeak() {
        return new Result<>(1006, "密码强度不足");
    }
    
    public static <T> Result<T> usernameInvalid() {
        return new Result<>(1007, "用户名格式不合法");
    }
    
    // Getter 和 Setter
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMsg() {
        return msg;
    }
    
    public void setMsg(String msg) {
        this.msg = msg;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
}
