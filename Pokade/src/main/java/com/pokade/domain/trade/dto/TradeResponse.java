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
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        LocalDateTime createdAt,
        Integer pointsUsed
) {

    public static TradeResponse of(Trade trade, String cardName, Integer pointsUsed) {
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
                trade.getRecipientName(),
                trade.getRecipientPhone(),
                trade.getRecipientAddress(),
                trade.getCreatedAt(),
                pointsUsed
        );
    }
}
