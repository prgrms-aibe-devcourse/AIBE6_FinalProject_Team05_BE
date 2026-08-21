package com.pokade.domain.price.dto;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;

public record BuyOfferOrderbookEntryResponse(
        Long buyOfferId,
        Integer price,
        ListingGrade grade
) {

    public static BuyOfferOrderbookEntryResponse of(BuyOffer buyOffer) {
        return new BuyOfferOrderbookEntryResponse(buyOffer.getId(), buyOffer.getPrice(), buyOffer.getGrade());
    }
}
