package com.pokade.domain.price.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 토스페이먼츠 결제창 successUrl 리다이렉트 쿼리 파라미터(paymentKey, orderId, amount)를 그대로 받는다.
// TradePaymentConfirmRequest와 달리 paymentKey는 필수가 아니다 - 포인트로 전액을 충당해 토스 결제
// 자체가 없는 주문(주문의 결제 금액이 0원)은 paymentKey 없이 이 엔드포인트를 바로 호출하기 때문
// (PriceService.confirmBuyOfferPurchase()가 결제 금액이 0보다 클 때만 paymentKey를 요구한다).
public record BuyOfferPaymentConfirmRequest(
        String paymentKey,
        @NotBlank(message = "orderId는 필수입니다.")
        String orderId,
        @NotNull(message = "amount는 필수입니다.")
        Long amount
) {
}
