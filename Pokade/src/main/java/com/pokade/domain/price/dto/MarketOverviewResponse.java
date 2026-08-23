package com.pokade.domain.price.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketOverviewResponse(
        long todayVolume,
        BigDecimal volumeChangeRate,
        Long todayMedianPrice,
        BigDecimal medianChangeRate1d,
        Long medianChangeAmount1d,
        BigDecimal medianChangeRate7d,
        BigDecimal medianChangeRate30d,
        long totalVolume,
        List<DailyMarketStatResponse> dailyStats
) {
}
