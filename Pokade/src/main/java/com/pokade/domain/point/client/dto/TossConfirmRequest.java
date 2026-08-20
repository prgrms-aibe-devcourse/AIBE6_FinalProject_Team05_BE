package com.pokade.domain.point.client.dto;

// 토스페이먼츠 결제 승인 API(POST /v1/payments/confirm) 요청 바디.
public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
