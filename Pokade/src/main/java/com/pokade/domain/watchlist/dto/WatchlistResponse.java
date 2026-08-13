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

    /** 등록 직후 응답 - 아직 카드/현재 시세를 조회하지 않은 상태라 카드 정보·currentPrice·등락률은 없다. */
    public static WatchlistResponse of(Watchlist watchlist) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                null,
                null,
                null,
                watchlist.getTargetBuyPrice(),
                watchlist.getTargetSellPrice(),
                watchlist.isNotified(),
                watchlist.getCreatedAt(),
                null,
                null,
                false
        );
    }

    /**
     * 목록 조회 응답 - 배치로 조회한 카드 정보·현재 시세·등락률을 함께 담는다.
     * targetReached는 "지금 시세가 목표가 대비 얼마인지"가 아니라 "체결가가 그동안 한 번이라도 그
     * 목표가를 지나간 적이 있는지"로 판정하므로(WatchlistService 참고), 여기서는 그 결과를 그대로 받는다.
     * changeRate는 PriceStatsResponse/PriceRankingResponse와 같은 최근 7일 vs 이전 7일 S등급 평균
     * 체결가 비교(%)이고, 데이터가 부족하면 0으로 채운다(랭킹처럼 항목 자체를 빼지 않음).
     */
    public static WatchlistResponse withPrice(
            Watchlist watchlist, Card card, CardPriceSummaryResponse currentPrice,
            BigDecimal changeRate, boolean targetReached) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getCardId(),
                watchlist.getVariantId(),
                card != null ? card.getName() : null,
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
