package com.pokade.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 가능한 파일 용량을 초과했습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "jpg 또는 png 이미지만 업로드할 수 있습니다."),
    PROFILE_IMAGE_NOT_SET(HttpStatus.BAD_REQUEST, "설정된 프로필 이미지가 없습니다."),
    INVALID_LISTING_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 매물입니다."),
    INVALID_TRADE_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 거래입니다."),
    SELF_PURCHASE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 등록한 매물은 구매할 수 없습니다."),
    SELF_BUY_OFFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 등록한 구매입찰은 즉시판매할 수 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "잘못된 기간 값입니다."),
    INVALID_RANKING_TYPE(HttpStatus.BAD_REQUEST, "잘못된 랭킹 타입입니다."),

    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    TRADE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "구매 주문을 찾을 수 없습니다."),
    BUY_OFFER_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "구매입찰 주문을 찾을 수 없습니다."),
    BUY_OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "구매입찰을 찾을 수 없습니다."),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    GRADE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "진단 결과를 찾을 수 없습니다."),
    PRIMARY_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "대표 변형이 지정되지 않은 카드입니다."),
    WATCHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "워치리스트 항목을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    INQUIRY_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "첨부 이미지는 최대 3장까지 가능합니다."),

    DUPLICATE_LISTING(HttpStatus.CONFLICT, "이미 등록된 매물입니다."),
    TRADE_CONFLICT(HttpStatus.CONFLICT, "이미 처리 중인 거래입니다."),
    TRADE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 구매 주문입니다."),
    BUY_OFFER_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 구매입찰 주문입니다."),
    BUY_OFFER_ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 체결되었거나 만료된 구매입찰입니다."),

    // ===== 인증 (Auth) =====
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    LOGIN_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    INVALID_OAUTH2_TICKET(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 가입 요청입니다."),
    TOKEN_STOLEN(HttpStatus.UNAUTHORIZED, "비정상적인 접근이 감지되어 로그아웃되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증이 완료된 계정입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "인증 코드 발송에 실패했습니다."),
    EMAIL_SEND_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),
    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_VERIFY_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다."),

    // ===== 마이페이지 (내 정보 수정) =====
    NICKNAME_CHANGE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "닉네임은 마지막 변경 후 30일이 지나야 다시 변경할 수 있습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),

    // ===== 회원 탈퇴 =====
    WITHDRAWAL_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 탈퇴 신청을 할 수 없습니다."),
    NOT_WITHDRAWAL_PENDING(HttpStatus.CONFLICT, "탈퇴 진행 중인 계정이 아닙니다."),

    // ===== 회원 정지 =====
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다. 고객센터에 문의해주세요."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "현재 계정 상태에서는 이용할 수 없는 기능입니다."),
    ADMIN_CANNOT_TARGET_SELF(HttpStatus.BAD_REQUEST, "본인 계정에는 수행할 수 없습니다."),
    ADMIN_CANNOT_TARGET_ADMIN(HttpStatus.BAD_REQUEST, "관리자 계정에는 수행할 수 없습니다."),
    ALREADY_SUSPENDED(HttpStatus.BAD_REQUEST, "이미 정지된 계정입니다."),
    NOT_SUSPENDED(HttpStatus.BAD_REQUEST, "정지 상태가 아닌 계정입니다."),
    ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "이미 탈퇴 처리된 계정입니다."),
    SUSPEND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "활성 상태의 계정만 정지할 수 있습니다."),

    // ===== AI 등급 진단 =====
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 등급 진단 서비스에 일시적인 오류가 발생했습니다."),

    // ===== 시세 챗봇 =====
    CHAT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챗봇 서비스에 일시적인 오류가 발생했습니다."),
    CHAT_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "같은 질문을 너무 많이 반복했어요. 1분 후 다시 시도해주세요."),

    // ===== 인프라 (AOP 자동 변환) =====
    FILE_IO_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 처리 중 오류가 발생했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 처리 중 오류가 발생했습니다."),

    // ===== 카드 도메인 임시 Rate Limit (팀 공통 정책 확정 시 제거) =====
    CARD_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "카드 API 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // ===== 워치리스트 도메인 =====
    DUPLICATE_WATCHLIST(HttpStatus.CONFLICT, "이미 등록된 카드입니다."),
    TARGET_PRICE_REQUIRED(HttpStatus.BAD_REQUEST, "목표 구매가 또는 판매가 중 하나는 입력해야 합니다."),
    WATCHLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "워치리스트는 최대 20개까지 등록할 수 있습니다."),
    NOTIFICATION_ALREADY_READ(HttpStatus.BAD_REQUEST, "이미 읽음 처리된 알림입니다."),

    // ===== 포트폴리오 =====
    PORTFOLIO_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 항목을 찾을 수 없습니다."),
    PORTFOLIO_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "시세 정보가 없어 손익을 계산할 수 없습니다."),
    PORTFOLIO_ACQUIRED_PRICE_REQUIRED(HttpStatus.BAD_REQUEST, "취득가가 입력되지 않아 손익을 계산할 수 없습니다."),
    GRADE_RESULT_NOT_REGISTRABLE(HttpStatus.BAD_REQUEST, "정상 산출된 진단 결과만 도감에 등록할 수 있습니다."),
    GRADE_RESULT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 도감에 등록된 진단 결과입니다."),

    // ===== Scrydex 동기화 배치 (관리자 트리거) =====
    SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 동기화가 진행 중입니다."),

    // ===== 포인트 =====
    INSUFFICIENT_POINT_BALANCE(HttpStatus.PAYMENT_REQUIRED, "포인트 잔액이 부족합니다."),
    POINT_CHARGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "충전 주문을 찾을 수 없습니다."),
    POINT_CHARGE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 충전 주문입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
