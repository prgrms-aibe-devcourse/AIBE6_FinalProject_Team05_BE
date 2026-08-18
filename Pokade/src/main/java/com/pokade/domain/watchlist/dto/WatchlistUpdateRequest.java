package com.pokade.domain.watchlist.dto;

import jakarta.validation.constraints.Positive;

public record WatchlistUpdateRequest(
        @Positive(message = "targetBuyPrice는 0보다 커야 합니다.")
        Integer targetBuyPrice,

        @Positive(message = "targetSellPrice는 0보다 커야 합니다.")
        Integer targetSellPrice
) {
}
