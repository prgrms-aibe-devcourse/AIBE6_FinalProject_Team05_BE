package com.pokade.domain.listing.service;

import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.global.port.ListingCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingCounter implements ListingCountPort {

    private final ListingRepository listingRepository;

    @Override
    public long countActiveListings(Long sellerId) {
        return listingRepository.countBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE);
    }
}
