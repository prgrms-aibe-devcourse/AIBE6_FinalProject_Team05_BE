package com.pokade.domain.price.dto;

import com.pokade.domain.price.entity.CardPrice;

import java.math.BigDecimal;

public record PriceRankingResponse(
        Long cardId,
        String cardName,
        String imageUrl,
        String grade,
        String company,
        BigDecimal price,
        String currency,
        BigDecimal changeRate
) {

    public static PriceRankingResponse of(CardPrice cardPrice) {
        var card = cardPrice.getVariant().getCard();
        return new PriceRankingResponse(
                card.getId(),
                card.getName(),
                card.getImageSmall(),
                cardPrice.getGrade(),
                cardPrice.getCompany(),
                cardPrice.getMarket(),
                cardPrice.getCurrency(),
                cardPrice.getChange7dPct()
        );
    }
}
