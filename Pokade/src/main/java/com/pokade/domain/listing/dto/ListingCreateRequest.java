package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ListingCreateRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        Integer price,

        ListingGrade grade,

        @NotEmpty(message = "사진은 최소 1장 이상 등록해야 합니다.")
        List<String> imageUrls
) {
}
