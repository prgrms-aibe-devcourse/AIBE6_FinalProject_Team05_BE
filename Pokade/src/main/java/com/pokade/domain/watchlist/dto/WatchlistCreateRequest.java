package com.pokade.domain.watchlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WatchlistCreateRequest(
        @NotNull(message = "cardId는 필수입니다.")
        Long cardId,

        Long variantId,

        @Positive(message = "targetBuyPrice는 0보다 커야 합니다.")
        Integer targetBuyPrice,

        @Positive(message = "targetSellPrice는 0보다 커야 합니다.")
        Integer targetSellPrice
) {
}
