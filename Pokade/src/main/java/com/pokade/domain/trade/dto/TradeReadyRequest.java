package com.pokade.domain.trade.dto;

import jakarta.validation.constraints.NotNull;

public record TradeReadyRequest(
        @NotNull(message = "listingId는 필수입니다.")
        Long listingId
) {
}
