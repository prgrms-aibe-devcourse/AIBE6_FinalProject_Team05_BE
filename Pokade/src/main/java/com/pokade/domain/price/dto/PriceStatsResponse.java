package com.pokade.domain.price.dto;

import java.math.BigDecimal;

public record PriceStatsResponse(
        BigDecimal changeRate,
        // card_prices 기반 조회(grade/period 지정 시)는 change_7d_amount 컬럼만 있어 7일 외 기간은 금액을 못 구한다 - 그때는 null.
        Long changeAmount,
        long volume
) {
}
