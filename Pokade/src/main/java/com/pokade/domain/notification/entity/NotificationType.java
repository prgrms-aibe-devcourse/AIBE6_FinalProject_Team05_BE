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
    INQUIRY_RECEIVED,

    // #392: 거래 진행 단계 알림 3종. 기존 TRADE_CONFIRMED(구매확정·정산 완료)와 나란히 읽히도록
    // TRADE_ 접두사를 공유한다.
    // 결제가 완료돼 판매자가 상품을 플랫폼으로 발송해야 하는 시점 - 판매자가 움직이지 않으면 거래가 멈춘다.
    TRADE_SHIPPING_REQUIRED,
    // 검수를 마친 상품이 구매자에게 배송 완료된 시점 - 구매자가 확정해야 판매자 정산이 이뤄진다.
    TRADE_DELIVERED,
    // 거래가 취소된 시점 - 취소를 누른 당사자가 아니라 그 사실을 모르는 상대방에게 간다.
    TRADE_CANCELLED,

    // #392: 등록해 둔 구매 입찰이 판매자의 즉시판매로 체결된 시점 - 입찰자는 아무 행동도 하지 않았는데
    // 거래가 시작되므로 알림이 없으면 알 방법이 없다. 수신자 관점이 "거래"가 아니라 "내 입찰"이라
    // TRADE_ 접두사를 쓰지 않는다.
    BUY_OFFER_MATCHED,

    // 구매 입찰이 새로 등록된 시점 - 그 카드에 매물을 올려둔 판매자에게 간다. BUY_OFFER_MATCHED가
    // "이미 팔렸다"를 알리는 사후 통보라면 이쪽은 "이 값에 팔 수 있다"를 알리는 사전 기회다.
    // 입찰 계열끼리 나란히 읽히도록 BUY_OFFER_ 접두사를 공유하고, INQUIRY_RECEIVED와 같은 뜻으로
    // (= 내가 처리할 것이 들어왔다) _RECEIVED를 쓴다.
    // INQUIRY_RECEIVED와 마찬가지로 수신자가 여럿이다 - 한 입찰에 판매자 여러 명이 동시에 받는다.
    BUY_OFFER_RECEIVED
}
