package com.trustai.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class R<T> {
    private int code;         // 20000成功，40000参数错误，40100未授权，50000系统异常
    private String msg;
    private T data;
    private long timestamp;

    public static <T> R<T> ok(T data) {
        return new R<>(20000, "success", data, System.currentTimeMillis());
    }
    public static R<Void> okMsg(String msg) {
        return new R<>(20000, msg, null, System.currentTimeMillis());
    }
    public static <T> R<T> error(int code, String msg) {
        return new R<>(code, msg, null, System.currentTimeMillis());
    }
    public static <T> R<T> error(String msg) {
        return error(50000, msg);
    }

    // 兼容更规范的响应字段命名：{ code, message, data }
    @JsonProperty("message")
    public String getMessage() {
        return msg;
    }
}
