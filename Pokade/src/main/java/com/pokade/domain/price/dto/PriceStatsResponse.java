package com.pokade.domain.price.dto;

import java.math.BigDecimal;

public record PriceStatsResponse(
        BigDecimal changeRate,
        long changeAmount,
        long volume
) {
}
