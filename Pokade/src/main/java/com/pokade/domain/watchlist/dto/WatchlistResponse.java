package com.pokade.domain.watchlist.dto;

import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.watchlist.entity.Watchlist;

import java.time.LocalDateTime;

public record WatchlistResponse(
        Long id,
        Long cardId,
        Long variantId,
        Integer targetBuyPrice,
        Integer targetSellPrice,
        boolean isNotified,
        LocalDateTime createdAt,
        CardPriceSummaryResponse currentPrice,
        boolean targetReached
) {

    /** 등록 직후 응답 - 아직 현재 시세를 조회하지 않은 상태라 currentPrice는 없다. */
    public static WatchlistResponse of(Watchlist watchlist) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt(),
                null,
                false
        );
    }

    /** 목록 조회 응답 - 배치로 조회한 현재 시세를 함께 담아 목표가 도달 여부까지 판단한다. */
    public static WatchlistResponse withPrice(Watchlist watchlist, CardPriceSummaryResponse currentPrice) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt(),
                currentPrice,
                isTargetReached(watchlist, currentPrice)
        );
    }

    /**
     * 매수 목표가는 현재가(buyPrice)가 목표가 이하로 떨어졌을 때, 매도 목표가는 현재가(sellPrice)가
     * 목표가 이상으로 올랐을 때 도달로 본다. 둘 다 설정돼 있으면 하나만 도달해도 true.
     */
    private static boolean isTargetReached(Watchlist watchlist, CardPriceSummaryResponse currentPrice) {
        if (currentPrice == null) {
            return false;
        }
        Integer targetBuyPrice = watchlist.getTargetBuyPrice();
        if (targetBuyPrice != null && currentPrice.buyPrice() != null
                && currentPrice.buyPrice() <= targetBuyPrice) {
            return true;
        }
        Integer targetSellPrice = watchlist.getTargetSellPrice();
        return targetSellPrice != null && currentPrice.sellPrice() != null
                && currentPrice.sellPrice() >= targetSellPrice;
    }
}
