package com.pokade.domain.admin.dto.response;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;

// 관리자 거래 관리 화면 전용 - TradeResponse에 판매자/구매자 닉네임을 더한 것.
// 일반 사용자용 MyTradeResponse.counterpartyId는 상대방 닉네임을 의도적으로 안 내려주는 기존 결정이 있어,
// TradeResponse 자체를 바꾸지 않고 이 관리자 전용 DTO를 새로 둔다(AdminTradeController에서만 쓴다).
public record AdminTradeResponse(
        Long id,
        Long listingId,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        Long cardId,
        String cardName,
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

    public static AdminTradeResponse of(TradeResponse trade, String buyerNickname, String sellerNickname) {
        return new AdminTradeResponse(
                trade.id(),
                trade.listingId(),
                trade.buyerId(),
                buyerNickname,
                trade.sellerId(),
                sellerNickname,
                trade.cardId(),
                trade.cardName(),
                trade.cardNameKo(),
                trade.cardImageUrl(),
                trade.grade(),
                trade.price(),
                trade.status(),
                trade.shippedAt(),
                trade.inspectedAt(),
                trade.deliveredAt(),
                trade.confirmedAt(),
                trade.settledAt(),
                trade.recipientName(),
                trade.recipientPhone(),
                trade.recipientAddress(),
                trade.createdAt(),
                trade.pointsUsed()
        );
    }
}
