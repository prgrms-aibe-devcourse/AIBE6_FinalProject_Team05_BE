package com.pokade.domain.trade.dto;

import com.pokade.domain.listing.entity.ListingGrade;
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
        // 한글 매핑이 없으면 null(어설픈 오번역보다 안전하다는 팀 컨벤션, domain.ai/domain.portfolio와 동일).
        // 표시할 땐 cardNameKo ?? cardName.
        String cardNameKo,
        String cardImageUrl,
        ListingGrade grade,
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

    public static TradeResponse of(
            Trade trade, String cardName, String cardNameKo, String cardImageUrl, Integer pointsUsed) {
        return new TradeResponse(
                trade.getId(),
                trade.getListing().getId(),
                trade.getBuyerId(),
                trade.getListing().getSellerId(),
                trade.getListing().getCardId(),
                cardName,
                cardNameKo,
                cardImageUrl,
                trade.getListing().getGrade(),
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
