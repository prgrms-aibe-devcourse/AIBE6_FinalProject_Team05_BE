package com.pokade.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_LISTING_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 매물입니다."),
    INVALID_TRADE_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 거래입니다."),
    SELF_PURCHASE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 등록한 매물은 구매할 수 없습니다."),

    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),

    DUPLICATE_LISTING(HttpStatus.CONFLICT, "이미 등록된 매물입니다."),
    TRADE_CONFLICT(HttpStatus.CONFLICT, "이미 처리 중인 거래입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
