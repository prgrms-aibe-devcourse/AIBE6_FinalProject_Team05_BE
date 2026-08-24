package com.pokade.domain.price.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketOverviewResponse(
        long todayVolume,
        BigDecimal volumeChangeRate,
        long volumeChangeAmount,
        Long todayAvgPrice,
        BigDecimal avgChangeRate1d,
        Long avgChangeAmount1d,
        BigDecimal avgChangeRate7d,
        BigDecimal avgChangeRate30d,
        long totalVolume,
        List<DailyMarketStatResponse> dailyStats
) {
}
