package com.pokade.domain.price.dto;

import java.math.BigDecimal;

public record PriceRankingResponse(
        Long cardId,
        String cardName,
        String imageUrl,
        long price,
        BigDecimal changeRate,
        long changeAmount
) {
}
