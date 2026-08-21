package com.pokade.domain.listing.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
import com.pokade.domain.listing.dto.OrderbookEntryResponse;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.event.ListingCreatedEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

    private final ListingRepository listingRepository;
    private final TradeRepository tradeRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final UserAccessChecker userAccessChecker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ListingResponse createListing(Long sellerId, ListingCreateRequest request) {
        userAccessChecker.assertWritable(sellerId);
        validateNotDuplicate(sellerId, request.cardId(), request.variantId());

        Listing listing = Listing.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .variantId(request.variantId())
                .price(request.price())
                .grade(request.grade())
                .build();

        Listing saved = listingRepository.save(listing);
        eventPublisher.publishEvent(new ListingCreatedEvent(saved.getCardId(), saved.getVariantId()));
        return ListingResponse.of(saved);
    }

    public List<ListingSummaryResponse> getActiveListings(Long cardId) {
        String cardName = cardRepository.findById(cardId).map(Card::getName).orElse(null);

        return listingRepository.findByCardIdAndStatusOrderByPriceAsc(cardId, ListingStatus.ACTIVE)
                .stream()
                .map(listing -> ListingSummaryResponse.of(listing, cardName))
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

    public Page<ListingSummaryResponse> getMyListings(Long sellerId, ListingStatus status, Pageable pageable) {
        Page<Listing> listingsPage = status != null
                ? listingRepository.findBySellerIdAndStatus(sellerId, status, pageable)
                : listingRepository.findBySellerId(sellerId, pageable);

        List<Listing> listings = listingsPage.getContent();
        List<Long> cardIds = listings.stream().map(Listing::getCardId).distinct().toList();
        Map<Long, String> cardNamesById = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Card::getName));

        // 거래 진행 상황 화면으로 연결하기 위해 매물별 거래 ID를 배치 조회 (건건이 조회하면 목록 크기만큼 쿼리가 나간다).
        List<Long> listingIds = listings.stream().map(Listing::getId).toList();
        Map<Long, Long> tradeIdByListingId = tradeRepository.findByListingIdIn(listingIds).stream()
                .collect(Collectors.toMap(trade -> trade.getListing().getId(), Trade::getId));

        return listingsPage.map(listing -> ListingSummaryResponse.of(
                listing,
                cardNamesById.get(listing.getCardId()),
                tradeIdByListingId.get(listing.getId())));
    }

    @Transactional
    public ListingResponse updatePrice(Long sellerId, Long listingId, ListingUpdateRequest request) {
        userAccessChecker.assertWritable(sellerId);
        Listing listing = getOwnedListing(sellerId, listingId);

        listing.changePrice(request.price());

        return ListingResponse.of(listing);
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
