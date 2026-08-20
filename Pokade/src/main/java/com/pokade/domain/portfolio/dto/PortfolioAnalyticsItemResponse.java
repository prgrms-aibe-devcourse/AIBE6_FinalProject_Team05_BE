package com.pokade.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioAnalyticsItemResponse(
        String label,
        BigDecimal value,
        BigDecimal ratio
) {
}
