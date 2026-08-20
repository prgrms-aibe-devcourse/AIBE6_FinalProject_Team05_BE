package com.pokade.domain.portfolio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record PortfolioItemAddRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity,

        @Min(value = 0, message = "취득가는 0 이상이어야 합니다.")
        Integer acquiredPrice,

        LocalDateTime acquiredAt
) {
}
