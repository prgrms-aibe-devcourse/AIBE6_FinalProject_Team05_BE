package com.pokade.domain.notification.entity;

// schema.sql 주석에 "등"이라고 돼있어 추후 알림 종류가 추가될 수 있음
public enum NotificationType {
    PRICE_TARGET,
    TRADE_CONFIRMED,
    LISTING_STALE,
    INQUIRY_HANDLED,
    LISTING_AVAILABLE,
    // #392: 사용자가 1:1 문의를 등록했을 때 관리자(ROLE_ADMIN)에게 가는 알림. 지금까지의 값들과 달리
    // 수신자가 일반 사용자가 아니라 관리자다 - 다만 저장 테이블(notifications)과 조회 API
    // (GET /api/notifications)는 그대로 공유하므로, 관리자 계정에서는 자기 알림 목록에 일반 알림과
    // 섞여 보인다. 관리자 전용 알림함은 별도 설계 대상이다.
    // 이 값은 @Enumerated(EnumType.STRING)으로 VARCHAR에 그대로 저장되고 DB CHECK 제약이 없어
    // 스키마 마이그레이션이 필요하지 않다.
    INQUIRY_RECEIVED
}
