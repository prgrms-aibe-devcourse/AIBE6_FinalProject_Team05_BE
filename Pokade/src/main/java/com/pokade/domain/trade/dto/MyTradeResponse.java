package com.pokade.domain.trade.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;

public record MyTradeResponse(
        Long tradeId,
        Long listingId,
        Long cardId,
        String cardName,
        String cardImageUrl,
        Integer price,
        TradeStatus status,
        TradeRole role,
        Long counterpartyId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static MyTradeResponse of(Trade trade, Long userId, Card card) {
        boolean isBuyer = trade.getBuyerId().equals(userId);
        return new MyTradeResponse(
                trade.getId(),
                trade.getListing().getId(),
                trade.getListing().getCardId(),
                card == null ? null : card.getName(),
                card == null ? null : card.getImageSmall(),
                trade.getPrice(),
                trade.getStatus(),
                isBuyer ? TradeRole.BUY : TradeRole.SELL,
                isBuyer ? trade.getListing().getSellerId() : trade.getBuyerId(),
                trade.getCreatedAt(),
                trade.getConfirmedAt()
        );
    }
}
