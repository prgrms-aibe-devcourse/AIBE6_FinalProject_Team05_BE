package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;

public record ListingSummaryResponse(
        Long id,
        Long sellerId,
        Long cardId,
        String cardName,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        String thumbnailUrl,
        LocalDateTime createdAt
) {

    public static ListingSummaryResponse of(Listing listing, String cardName) {
        String thumbnailUrl = listing.getImages().isEmpty()
                ? null
                : listing.getImages().get(0).getImageUrl();

        return new ListingSummaryResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getCardId(),
                cardName,
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                thumbnailUrl,
                listing.getCreatedAt()
        );
    }
}
