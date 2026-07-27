package com.pokade.domain.listing;

import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
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

    private void validateNotDuplicate(Long sellerId, Long cardId, Long variantId) {
        // TODO: "중복 등록" 기준 팀 확정 필요 (현재는 동일 판매자가 같은 카드/variant에 ACTIVE 매물을 이미 가진 경우로 간주)
        boolean exists = listingRepository.existsBySellerIdAndCardIdAndVariantIdAndStatus(
                sellerId, cardId, variantId, ListingStatus.ACTIVE);
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_LISTING);
        }
    }
}
