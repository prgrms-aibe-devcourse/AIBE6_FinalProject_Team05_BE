package com.pokade.domain.chat.dto;

import com.pokade.domain.price.dto.PriceRankingResponse;

import java.math.BigDecimal;

public record ChatRankingItemResponse(
        Long cardId,
        String cardName,
        String cardNameKo,
        long price,
        BigDecimal changeRate,
        long changeAmount
) {

    public static ChatRankingItemResponse from(PriceRankingResponse r) {
        return new ChatRankingItemResponse(r.cardId(), r.cardName(), r.cardNameKo(), r.price(), r.changeRate(), r.changeAmount());
    }
}
