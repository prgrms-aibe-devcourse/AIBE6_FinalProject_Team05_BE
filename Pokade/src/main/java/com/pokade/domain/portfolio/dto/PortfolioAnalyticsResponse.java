package com.pokade.domain.portfolio.dto;

import java.util.List;

public record PortfolioAnalyticsResponse(
        List<PortfolioAnalyticsItemResponse> bySet,
        List<PortfolioAnalyticsItemResponse> byRarity
) {
}
