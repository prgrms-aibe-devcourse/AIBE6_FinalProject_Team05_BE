package com.pokade.domain.trade.dto;

import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        Long listingId,
        Long buyerId,
        Long sellerId,
        Long cardId,
        String cardName,
        Integer price,
        TradeStatus status,
        LocalDateTime shippedAt,
        LocalDateTime inspectedAt,
        LocalDateTime deliveredAt,
        LocalDateTime confirmedAt,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {

    public static TradeResponse of(Trade trade, String cardName) {
        return new TradeResponse(
                trade.getId(),
                trade.getListing().getId(),
                trade.getBuyerId(),
                trade.getListing().getSellerId(),
                trade.getListing().getCardId(),
                cardName,
                trade.getPrice(),
                trade.getStatus(),
                trade.getShippedAt(),
                trade.getInspectedAt(),
                trade.getDeliveredAt(),
                trade.getConfirmedAt(),
                trade.getSettledAt(),
                trade.getCreatedAt()
        );
    }
}
