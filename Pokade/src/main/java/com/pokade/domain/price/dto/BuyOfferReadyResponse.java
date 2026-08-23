package com.pokade.domain.price.dto;

// TradeReadyResponse와 동일한 관례 - clientKey는 프론트가 자체 환경변수로 들고 있어 orderId/amount만
// 전달한다. amount는 상품가 + 배송비를 더한 최종 결제 금액이다(price 단독이 아님).
public record BuyOfferReadyResponse(String orderId, Integer amount) {
}
