package com.pokade.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioItemPnlResponse(
        Long id,
        Long cardId,
        Integer quantity,
        Integer acquiredPrice,
        BigDecimal currentMarketPrice,
        String currency,
        BigDecimal pnlAmount,
        BigDecimal pnlRate
) {
}
