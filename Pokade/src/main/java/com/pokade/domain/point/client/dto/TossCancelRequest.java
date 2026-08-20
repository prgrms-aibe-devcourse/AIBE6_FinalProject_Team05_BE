package com.pokade.domain.point.client.dto;

// 토스페이먼츠 결제취소 API(POST /v1/payments/{paymentKey}/cancel) 요청 바디.
public record TossCancelRequest(String cancelReason) {
}
