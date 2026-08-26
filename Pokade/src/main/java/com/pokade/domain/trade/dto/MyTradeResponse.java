package com.pokade.domain.trade.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;

public record MyTradeResponse(
        Long tradeId,
        Long listingId,
        Long cardId,
        String cardName,
        // 한글 매핑이 없으면 null(어설픈 오번역보다 안전하다는 팀 컨벤션, domain.ai/domain.portfolio와 동일).
        // 표시할 땐 cardNameKo ?? cardName.
        String cardNameKo,
        String cardImageUrl,
        ListingGrade grade,
        Integer price,
        TradeStatus status,
        TradeRole role,
        Long counterpartyId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static MyTradeResponse of(Trade trade, Long userId, Card card, String cardNameKo) {
        boolean isBuyer = trade.getBuyerId().equals(userId);
        return new MyTradeResponse(
                trade.getId(),
                trade.getListing().getId(),
                trade.getListing().getCardId(),
                card == null ? null : card.getName(),
                card == null ? null : cardNameKo,
                card == null ? null : card.getImageSmall(),
                trade.getListing().getGrade(),
                trade.getPrice(),
                trade.getStatus(),
                isBuyer ? TradeRole.BUY : TradeRole.SELL,
                isBuyer ? trade.getListing().getSellerId() : trade.getBuyerId(),
                trade.getCreatedAt(),
                trade.getConfirmedAt()
        );
    }
}
