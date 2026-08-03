package com.pokade.domain.price.dto;

public record CardPriceSummaryResponse(
        Long cardId,
        Integer buyPrice,
        Integer sellPrice,
        String currency
) {
}
