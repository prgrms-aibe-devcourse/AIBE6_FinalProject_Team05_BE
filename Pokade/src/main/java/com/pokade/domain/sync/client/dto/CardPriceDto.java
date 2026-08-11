package com.pokade.domain.sync.client.dto;

import java.math.BigDecimal;

public record CardPriceDto(
        String condition,
        String grade,
        String company,
        String type,
        BigDecimal low,
        BigDecimal mid,
        BigDecimal high,
        BigDecimal market,
        String currency
) {
}
