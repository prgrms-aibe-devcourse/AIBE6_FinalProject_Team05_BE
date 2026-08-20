package com.pokade.domain.chat.dto;

import com.pokade.domain.price.dto.PriceRankingResponse;

import java.math.BigDecimal;

public record ChatRankingItemResponse(
        String cardName,
        long price,
        BigDecimal changeRate,
        long changeAmount
) {

    public static ChatRankingItemResponse from(PriceRankingResponse r) {
        return new ChatRankingItemResponse(r.cardName(), r.price(), r.changeRate(), r.changeAmount());
    }
}
