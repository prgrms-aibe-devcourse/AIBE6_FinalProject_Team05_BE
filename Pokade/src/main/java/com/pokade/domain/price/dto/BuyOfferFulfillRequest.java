package com.pokade.domain.price.dto;

import jakarta.validation.constraints.NotBlank;

// 즉시판매(구매입찰 체결) 요청 - 가격/등급/받는사람 정보는 이미 구매입찰(BuyOffer)에 있으므로,
// 판매자에게 새로 받아야 하는 정산계좌·반송주소만 ListingCreateRequest와 동일한 필드로 받는다.
public record BuyOfferFulfillRequest(
        @NotBlank(message = "정산 받을 은행명은 필수입니다.")
        String settlementBankName,

        @NotBlank(message = "정산 받을 계좌번호는 필수입니다.")
        String settlementAccountNumber,

        @NotBlank(message = "정산 받을 예금주명은 필수입니다.")
        String settlementAccountHolder,

        @NotBlank(message = "반송받을 분 성함은 필수입니다.")
        String returnRecipientName,

        @NotBlank(message = "반송받을 분 연락처는 필수입니다.")
        String returnRecipientPhone,

        @NotBlank(message = "반송받을 주소는 필수입니다.")
        String returnAddress
) {
}
