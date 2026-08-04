package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;

public record ListingResponse(
        Long id,
        Long cardId,
        Long sellerId,
        Long variantId,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        LocalDateTime createdAt
) {

    public static ListingResponse of(Listing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getCardId(),
                listing.getSellerId(),
                listing.getVariantId(),
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                listing.getCreatedAt()
        );
    }
}
