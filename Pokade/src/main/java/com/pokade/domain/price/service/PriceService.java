package com.pokade.domain.price.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.ListingRepository;
import com.pokade.domain.listing.ListingStatus;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다: " + cardId);
        }

        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "대표 변형이 지정되지 않은 카드입니다: " + cardId));

        Integer buyPrice = listingRepository
                .findLowestActivePrice(cardId, resolvedVariantId, ListingStatus.ACTIVE)
                .orElse(null);
        Integer sellPrice = buyOfferRepository.findHighestActivePrice(cardId, resolvedVariantId).orElse(null);

        return new PriceSummaryResponse(buyPrice, sellPrice, CURRENCY);
    }
}
