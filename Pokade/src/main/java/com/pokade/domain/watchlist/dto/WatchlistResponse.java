package com.pokade.domain.watchlist.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.watchlist.entity.Watchlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WatchlistResponse(
        Long id,
        Long cardId,
        Long variantId,
        String cardName,
        String cardNameKo,
        String setName,
        String imageUrl,
        Integer targetBuyPrice,
        Integer targetSellPrice,
        boolean isNotified,
        LocalDateTime createdAt,
        CardPriceSummaryResponse currentPrice,
        BigDecimal changeRate,
        boolean targetReached
) {

    /** 등록/수정 직후 응답 - 아직 카드/현재 시세를 조회하지 않은 상태라 카드 정보·currentPrice·등락률은 없다. */
    public static WatchlistResponse of(Watchlist watchlist, boolean targetReached) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                null,
                null,
                null,
                null,
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt(),
                null,
                null,
                targetReached
        );
    }


    public static WatchlistResponse withPrice(
            Watchlist watchlist, Card card, String cardNameKo, CardPriceSummaryResponse currentPrice,
            BigDecimal changeRate, boolean targetReached) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                card != null ? card.getName() : null,
                cardNameKo,
                card != null ? card.getSetName() : null,
                card != null ? resolveImageUrl(card) : null,
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt(),
                currentPrice,
                changeRate,
                targetReached
        );
    }

    private static String resolveImageUrl(Card card) {
        return card.getImageMedium() != null ? card.getImageMedium() : card.getImageSmall();
    }
}
