package com.pokade.domain.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 토스페이먼츠 결제창 successUrl 리다이렉트 쿼리 파라미터(paymentKey, orderId, amount)를 그대로 받는다.
public record TradePaymentConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.")
        String paymentKey,
        @NotBlank(message = "orderId는 필수입니다.")
        String orderId,
        @NotNull(message = "amount는 필수입니다.")
        Long amount
) {
}
