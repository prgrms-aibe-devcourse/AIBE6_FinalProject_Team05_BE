package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.Listing;
import com.pokade.domain.listing.ListingGrade;

public record OrderbookEntryResponse(
        Long listingId,
        Integer price,
        ListingGrade grade
) {

    public static OrderbookEntryResponse of(Listing listing) {
        return new OrderbookEntryResponse(listing.getId(), listing.getPrice(), listing.getGrade());
    }
}
