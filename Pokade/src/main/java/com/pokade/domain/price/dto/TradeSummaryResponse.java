package com.pokade.domain.price.dto;

import com.pokade.domain.listing.ListingGrade;
import com.pokade.domain.trade.Trade;

import java.time.LocalDateTime;

public record TradeSummaryResponse(
        LocalDateTime tradedAt,
        Integer price,
        ListingGrade grade
) {

    public static TradeSummaryResponse of(Trade trade) {
        return new TradeSummaryResponse(
                trade.getConfirmedAt(),
                trade.getPrice(),
                trade.getListing().getGrade()
        );
    }
}
