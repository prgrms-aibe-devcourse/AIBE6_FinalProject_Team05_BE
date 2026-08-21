package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ListingCreateRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        Integer price,

        ListingGrade grade,

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
