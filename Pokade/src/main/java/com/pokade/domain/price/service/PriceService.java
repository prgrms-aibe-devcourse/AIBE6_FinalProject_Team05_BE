package com.pokade.domain.price.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.ListingRepository;
import com.pokade.domain.listing.ListingStatus;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceService {

    private static final String CURRENCY = "KRW";

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final ListingRepository listingRepository;
    private final BuyOfferRepository buyOfferRepository;

    public PriceSummaryResponse getSummary(Long cardId, Long variantId) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        Integer buyPrice = listingRepository
                .findLowestActivePrice(cardId, resolvedVariantId, ListingStatus.ACTIVE)
                .orElse(null);
        Integer sellPrice = buyOfferRepository.findHighestActivePrice(cardId, resolvedVariantId).orElse(null);

        return new PriceSummaryResponse(buyPrice, sellPrice, CURRENCY);
    }
}
