package com.pokade.domain.portfolio.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.portfolio.entity.PortfolioItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioItemResponse(
        Long id,
        Long cardId,
        String cardName,
        String cardImageSmall,
        Long variantId,
        String variantName,
        Integer quantity,
        Integer acquiredPrice,
        LocalDateTime acquiredAt,
        Long tradeId,
        // 대표 변형(raw NM) 기준 Scrydex 시세. 데이터 없으면 null
        BigDecimal currentMarketPrice,
        String currency
) {

    public static PortfolioItemResponse of(
            PortfolioItem item,
            Card card,
            CardVariant variant,
            CardPriceRepository.VariantMarketPriceView priceView
    ) {
        return new PortfolioItemResponse(
                item.getId(),
                item.getCardId(),
                card != null ? card.getName() : null,
                card != null ? card.getImageSmall() : null,
                item.getVariantId(),
                variant != null ? variant.getVariantName() : null,
                item.getQuantity(),
                item.getAcquiredPrice(),
                item.getAcquiredAt(),
                item.getTradeId(),
                priceView != null ? priceView.getMarket() : null,
                priceView != null ? priceView.getCurrency() : null
        );
    }
}
