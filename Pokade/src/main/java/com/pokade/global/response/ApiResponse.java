package com.pokade.global.response;

import com.pokade.global.exception.ErrorCode;
import lombok.NonNull;

public record ApiResponse<T>(
        int status,          // HTTP 상태 (예: 200, 409) — 파싱 안 함
        @NonNull String code, // 의미 있는 코드 ("OK", "DUPLICATE_EMAIL")
        @NonNull String msg,
        T data

) {
    public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(200, "OK", "요청이 성공했습니다.", data);
    }
    public static <T> ApiResponse<T> ok(String msg, T data) {
            return new ApiResponse<>(200, "OK", msg, data);
    }
    public static ApiResponse<Void> ok(String msg) {
            return new ApiResponse<>(200, "OK", msg, null);
    }
    public static ApiResponse<Void> fail(ErrorCode e) {
            return new ApiResponse<>(e.getStatus().value(), e.name(), e.getMessage(), null);
    }
}

