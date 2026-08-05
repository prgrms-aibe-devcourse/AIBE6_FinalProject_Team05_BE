package com.pokade.domain.sync.client.dto;

import java.math.BigDecimal;

public record CardPriceDto(
        String variantId,
        String priceType,
        String grade,
        String company,
        BigDecimal low,
        BigDecimal mid,
        BigDecimal high,
        BigDecimal market,
        String currency
) {
}
