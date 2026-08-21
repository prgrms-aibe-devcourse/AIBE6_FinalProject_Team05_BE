package com.pokade.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioSummaryResponse(
        BigDecimal totalValue,
        BigDecimal changeAmount,
        BigDecimal changeRate,
        String currency
) {
}
