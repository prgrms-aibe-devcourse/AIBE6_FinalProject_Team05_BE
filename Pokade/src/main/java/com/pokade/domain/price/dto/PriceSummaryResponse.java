package com.pokade.price.dto;

public record PriceSummaryResponse(
        Integer buyPrice,
        Integer sellPrice,
        String currency
) {
}
