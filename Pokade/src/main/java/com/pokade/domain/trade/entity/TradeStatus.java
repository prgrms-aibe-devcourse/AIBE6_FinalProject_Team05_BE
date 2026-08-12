package com.pokade.domain.trade.entity;

public enum TradeStatus {
    PENDING,              // 즉시구매 완료, 판매자 발송 대기
    SHIPPED_TO_PLATFORM,   // 판매자가 플랫폼으로 발송함, 검수 대기
    INSPECTED,             // 플랫폼 검수 완료, 구매자에게 배송 대기/중
    DELIVERED,             // 구매자에게 배송 완료, 구매자 확정 대기
    COMPLETED,             // 구매자 확정
    CANCELLED
}
