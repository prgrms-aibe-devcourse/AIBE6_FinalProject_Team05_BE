package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ListingCreateRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        Integer price,

        ListingGrade grade
) {
}
