package com.pokade.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_LISTING_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 매물입니다."),
    INVALID_TRADE_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 거래입니다."),
    SELF_PURCHASE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 등록한 매물은 구매할 수 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "잘못된 기간 값입니다."),

    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    PRIMARY_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "대표 변형이 지정되지 않은 카드입니다."),

    DUPLICATE_LISTING(HttpStatus.CONFLICT, "이미 등록된 매물입니다."),
    TRADE_CONFLICT(HttpStatus.CONFLICT, "이미 처리 중인 거래입니다."),

    // ===== 인증 (Auth) =====
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN,"이메일 인증이 완료되지 않았습니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증이 완료된 계정입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "인증 코드 발송에 실패했습니다."),
    EMAIL_SEND_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),
    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_VERIFY_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다."),

    // ===== AI 등급 진단 =====
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 등급 진단 서비스에 일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
