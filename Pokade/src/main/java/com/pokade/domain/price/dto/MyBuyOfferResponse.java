package com.pokade.domain.price.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;

import java.time.LocalDateTime;

public record MyBuyOfferResponse(
        Long buyOfferId,
        Long cardId,
        String cardName,
        String cardImageUrl,
        Long variantId,
        Integer price,
        ListingGrade grade,
        String status,
        LocalDateTime createdAt
) {

    public static MyBuyOfferResponse of(BuyOffer buyOffer, Card card) {
        return new MyBuyOfferResponse(
                buyOffer.getId(),
                buyOffer.getCardId(),
                card == null ? null : card.getName(),
                card == null ? null : card.getImageSmall(),
                buyOffer.getVariantId(),
                buyOffer.getPrice(),
                buyOffer.getGrade(),
                buyOffer.getStatus(),
                buyOffer.getCreatedAt()
        );
    }
}
