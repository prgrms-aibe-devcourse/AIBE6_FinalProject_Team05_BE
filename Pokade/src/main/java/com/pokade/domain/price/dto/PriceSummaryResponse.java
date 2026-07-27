package com.pokade.domain.price.dto;

public record PriceSummaryResponse(
        Integer buyPrice,
        Integer sellPrice,
        String currency
) {
}
