package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ListingResponse(
        Long id,
        Long cardId,
        Long sellerId,
        Long variantId,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        List<String> imageUrls,
        LocalDateTime createdAt
) {

    public static ListingResponse of(Listing listing, List<String> imageUrls) {
        return new ListingResponse(
                listing.getId(),
                listing.getCardId(),
                listing.getSellerId(),
                listing.getVariantId(),
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                imageUrls,
                listing.getCreatedAt()
        );
    }
}
