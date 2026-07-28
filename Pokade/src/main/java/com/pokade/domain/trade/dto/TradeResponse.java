package com.pokade.domain.trade.dto;

import com.pokade.domain.trade.Trade;
import com.pokade.domain.trade.TradeStatus;

import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        Long listingId,
        Long buyerId,
        Integer price,
        TradeStatus status,
        LocalDateTime shippedAt,
        LocalDateTime confirmedAt,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {

    public static TradeResponse of(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getListing().getId(),
                trade.getBuyerId(),
                trade.getPrice(),
                trade.getStatus(),
                trade.getShippedAt(),
                trade.getConfirmedAt(),
                trade.getSettledAt(),
                trade.getCreatedAt()
        );
    }
}
