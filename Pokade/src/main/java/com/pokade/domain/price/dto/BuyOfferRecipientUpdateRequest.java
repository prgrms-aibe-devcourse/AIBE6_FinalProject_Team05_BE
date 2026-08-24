package com.pokade.domain.price.dto;

import jakarta.validation.constraints.NotBlank;

// 마이페이지 "입찰" 주문서 화면에서 결제 완료된 구매입찰의 받는사람 정보를 수정할 때 쓴다.
public record BuyOfferRecipientUpdateRequest(
        @NotBlank(message = "받는 분 성함은 필수입니다.")
        String recipientName,

        @NotBlank(message = "받는 분 연락처는 필수입니다.")
        String recipientPhone,

        @NotBlank(message = "받는 분 주소는 필수입니다.")
        String recipientAddress
) {
}
