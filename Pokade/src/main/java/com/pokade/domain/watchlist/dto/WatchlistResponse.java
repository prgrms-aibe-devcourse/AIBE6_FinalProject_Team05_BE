package com.pokade.domain.watchlist.dto;

import com.pokade.domain.watchlist.entity.Watchlist;

import java.time.LocalDateTime;

public record WatchlistResponse(
        Long id,
        Long cardId,
        Long variantId,
        Integer targetBuyPrice,
        Integer targetSellPrice,
        boolean isNotified,
        LocalDateTime createdAt
) {

    public static WatchlistResponse of(Watchlist watchlist) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt()
        );
    }
}
