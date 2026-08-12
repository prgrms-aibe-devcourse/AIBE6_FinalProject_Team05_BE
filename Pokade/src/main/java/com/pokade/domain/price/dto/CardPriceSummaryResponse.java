package com.pokade.domain.price.dto;

import java.math.BigDecimal;

public record CardPriceSummaryResponse(
        Long cardId,
        Integer buyPrice,
        Integer sellPrice,
        Integer recentTradePrice,
        String currency,
        // card_prices의 비등급(raw) 시세 - buyPrice/recentTradePrice가 둘 다 없을 때 프론트의 fallback 표시용.
        // buyPrice/sellPrice/recentTradePrice는 항상 KRW(currency 필드)지만, marketPrice는 Scrydex 동기화 원본
        // 통화(USD/JPY 등)라 별도 marketPriceCurrency로 표시한다.
        BigDecimal marketPrice,
        String marketPriceCurrency
) {
}
