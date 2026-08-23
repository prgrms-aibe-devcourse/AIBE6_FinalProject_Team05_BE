package com.pokade.domain.price.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;
import java.time.LocalDateTime;

public record BuyOfferResponse(
        Long id,
        Long cardId,
        Long buyerId,
        Long variantId,
        Integer price,
        ListingGrade grade,
        String status,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        LocalDateTime createdAt
) {

    public static BuyOfferResponse of(BuyOffer buyOffer) {
        return new BuyOfferResponse(
                buyOffer.getId(),
                buyOffer.getCardId(),
                buyOffer.getBuyerId(),
                buyOffer.getVariantId(),
                buyOffer.getPrice(),
                buyOffer.getGrade(),
                buyOffer.getStatus(),
                buyOffer.getRecipientName(),
                buyOffer.getRecipientPhone(),
                buyOffer.getRecipientAddress(),
                buyOffer.getCreatedAt()
        );
    }
}
