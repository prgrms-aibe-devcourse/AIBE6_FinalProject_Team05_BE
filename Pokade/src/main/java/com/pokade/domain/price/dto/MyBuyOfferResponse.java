package com.pokade.domain.price.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;

import java.time.LocalDateTime;

public record MyBuyOfferResponse(
        Long buyOfferId,
        Long cardId,
        String cardName,
        // 한글 매핑이 없으면 null(어설픈 오번역보다 안전하다는 CardNameKoResolver의 설계). 표시할 땐
        // cardNameKo ?? cardName.
        String cardNameKo,
        String cardImageUrl,
        Long variantId,
        Integer price,
        ListingGrade grade,
        String status,
        Integer shippingFee,
        Integer pointsUsed,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        LocalDateTime createdAt
) {

    public static MyBuyOfferResponse of(BuyOffer buyOffer, Card card, String cardNameKo) {
        return new MyBuyOfferResponse(
                buyOffer.getId(),
                buyOffer.getCardId(),
                card == null ? null : card.getName(),
                cardNameKo,
                card == null ? null : card.getImageSmall(),
                buyOffer.getVariantId(),
                buyOffer.getPrice(),
                buyOffer.getGrade(),
                buyOffer.getStatus(),
                buyOffer.getShippingFee(),
                buyOffer.getPointsUsed(),
                buyOffer.getRecipientName(),
                buyOffer.getRecipientPhone(),
                buyOffer.getRecipientAddress(),
                buyOffer.getCreatedAt()
        );
    }
}
