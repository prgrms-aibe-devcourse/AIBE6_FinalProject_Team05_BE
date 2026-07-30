package com.pokade.domain.listing.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
import com.pokade.domain.listing.dto.OrderbookEntryResponse;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingImage;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

    private final ListingRepository listingRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;

    @Transactional
    public ListingResponse createListing(Long sellerId, ListingCreateRequest request) {
        validateNotDuplicate(sellerId, request.cardId(), request.variantId());

        Listing listing = Listing.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .variantId(request.variantId())
                .price(request.price())
                .grade(request.grade())
                .build();

        List<String> imageUrls = request.imageUrls();
        for (int i = 0; i < imageUrls.size(); i++) {
            listing.addImage(imageUrls.get(i), i);
        }

        Listing saved = listingRepository.save(listing);
        return ListingResponse.of(saved, imageUrls);
    }

    public List<ListingSummaryResponse> getActiveListings(Long cardId) {
        return listingRepository.findByCardIdAndStatusOrderByPriceAsc(cardId, ListingStatus.ACTIVE)
                .stream()
                .map(ListingSummaryResponse::of)
                .toList();
    }

    public List<OrderbookEntryResponse> getOrderbook(Long cardId, Long variantId, ListingGrade grade) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        return listingRepository
                .findOrderbook(cardId, resolvedVariantId, ListingStatus.ACTIVE, grade)
                .stream()
                .map(OrderbookEntryResponse::of)
                .toList();
    }

    public List<ListingSummaryResponse> getMyListings(Long sellerId, ListingStatus status) {
        List<Listing> listings = status != null
                ? listingRepository.findBySellerIdAndStatus(sellerId, status)
                : listingRepository.findBySellerId(sellerId);

        return listings.stream()
                .map(ListingSummaryResponse::of)
                .toList();
    }

    @Transactional
    public ListingResponse updatePrice(Long sellerId, Long listingId, ListingUpdateRequest request) {
        Listing listing = getOwnedListing(sellerId, listingId);

        listing.changePrice(request.price());

        List<String> imageUrls = listing.getImages().stream()
                .map(ListingImage::getImageUrl)
                .toList();
        return ListingResponse.of(listing, imageUrls);
    }

    @Transactional
    public void deleteListing(Long sellerId, Long listingId) {
        Listing listing = getOwnedListing(sellerId, listingId);
        listing.cancel();
    }

    private Listing getOwnedListing(Long sellerId, Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return listing;
    }

    private void validateNotDuplicate(Long sellerId, Long cardId, Long variantId) {
        // TODO: "중복 등록" 기준 팀 확정 필요 (현재는 동일 판매자가 같은 카드/variant에 ACTIVE 매물을 이미 가진 경우로 간주)
        boolean exists = listingRepository.existsBySellerIdAndCardIdAndVariantIdAndStatus(
                sellerId, cardId, variantId, ListingStatus.ACTIVE);
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_LISTING);
        }
    }
}
