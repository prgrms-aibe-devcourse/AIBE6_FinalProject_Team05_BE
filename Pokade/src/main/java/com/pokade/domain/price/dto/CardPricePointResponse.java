package com.pokade.domain.price.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// card_prices의 change_*_pct(등락률)로부터 역산한 시점별 가격 포인트. 실제 체결 이력(TradeSummaryResponse)이 아니라
// "현재가 market을 등락률만큼 거슬러 올라간 추정값"이라 실거래 데이터와는 다른 성격의 값이다.
public record CardPricePointResponse(
        LocalDateTime date,
        BigDecimal price,
        String currency
) {
}
