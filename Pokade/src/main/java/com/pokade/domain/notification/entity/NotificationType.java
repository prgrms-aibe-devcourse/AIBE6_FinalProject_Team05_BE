package com.pokade.domain.notification.entity;

// schema.sql 주석에 "등"이라고 돼있어 추후 알림 종류가 추가될 수 있음
public enum NotificationType {
    PRICE_TARGET,
    TRADE_CONFIRMED,
    LISTING_STALE,
    INQUIRY_HANDLED
}
