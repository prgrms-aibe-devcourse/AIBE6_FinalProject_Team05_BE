package com.pokade.domain.price.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 구매입찰 등록 결제 준비 - 등록과 동시에 토스 에스크로 결제를 진행하므로, 등록에 필요한 정보와
// 받는사람 정보를 한 번에 받는다(TradeReadyRequest는 매물이 이미 있어 listingId만 받지만,
// 구매입찰은 아직 아무 것도 존재하지 않는 새 입찰이라 등록 정보 자체를 여기서 받아야 한다).
public record BuyOfferReadyRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        Integer price,

        ListingGrade grade,

        @NotNull(message = "pointsToUse는 필수입니다.")
        @Min(value = 0, message = "pointsToUse는 0 이상이어야 합니다.")
        Integer pointsToUse,

        @NotBlank(message = "받는사람 이름은 필수입니다.")
        String recipientName,

        @NotBlank(message = "받는사람 전화번호는 필수입니다.")
        String recipientPhone,

        @NotBlank(message = "받는사람 주소는 필수입니다.")
        String recipientAddress
) {
}
