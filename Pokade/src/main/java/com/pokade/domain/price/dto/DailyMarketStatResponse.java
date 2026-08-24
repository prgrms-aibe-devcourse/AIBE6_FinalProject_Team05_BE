package com.pokade.domain.price.dto;

import java.time.LocalDate;

public record DailyMarketStatResponse(
        LocalDate date,
        long volume,
        Long avgPrice
) {
}
