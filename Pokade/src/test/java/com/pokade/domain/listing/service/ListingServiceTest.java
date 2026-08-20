package com.pokade.domain.listing.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private UserAccessChecker userAccessChecker;

    @InjectMocks
    private ListingService listingService;

    @Test
    void 매물_등록시_판매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        ListingCreateRequest request = new ListingCreateRequest(1L, null, 10000, ListingGrade.A);
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(100L);

        assertThatThrownBy(() -> listingService.createListing(100L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void 매물_등록시_판매자_계정이_활성이면_정상_등록된다() {
        ListingCreateRequest request = new ListingCreateRequest(1L, null, 10000, ListingGrade.A);
        given(listingRepository.existsBySellerIdAndCardIdAndVariantIdAndStatus(
                anyLong(), any(), any(), any())).willReturn(false);
        given(listingRepository.save(any(Listing.class))).willAnswer(invocation -> invocation.getArgument(0));

        ListingResponse response = listingService.createListing(100L, request);

        assertThat(response.sellerId()).isEqualTo(100L);
        assertThat(response.price()).isEqualTo(10000);
    }

    @Test
    void 가격_수정시_판매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        ListingUpdateRequest request = new ListingUpdateRequest(20000);
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(100L);

        assertThatThrownBy(() -> listingService.updatePrice(100L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(listingRepository, never()).findById(anyLong());
    }

    @Test
    void 가격_수정시_판매자_계정이_활성이면_정상_수정된다() {
        Listing listing = Listing.builder()
                .cardId(1L)
                .sellerId(100L)
                .price(10000)
                .build();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));

        ListingResponse response = listingService.updatePrice(100L, 1L, new ListingUpdateRequest(20000));

        assertThat(response.price()).isEqualTo(20000);
    }

    @Test
    void 내_매물_목록_조회시_연결된_거래가_있으면_거래ID를_함께_반환한다() {
        Listing tradingListing = Listing.builder()
                .cardId(1L)
                .sellerId(100L)
                .price(10000)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(tradingListing, "id", 10L);

        Listing activeListing = Listing.builder()
                .cardId(2L)
                .sellerId(100L)
                .price(20000)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(activeListing, "id", 20L);

        Trade trade = Trade.builder().listing(tradingListing).buyerId(200L).price(10000).build();
        org.springframework.test.util.ReflectionTestUtils.setField(trade, "id", 500L);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        given(listingRepository.findBySellerId(100L, pageable))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(tradingListing, activeListing)));
        given(cardRepository.findAllById(any())).willReturn(List.of());
        given(tradeRepository.findByListingIdIn(any())).willReturn(List.of(trade));

        List<ListingSummaryResponse> responses = listingService.getMyListings(100L, null, pageable).getContent();

        assertThat(responses).hasSize(2);
        ListingSummaryResponse tradingSummary = responses.stream()
                .filter(r -> r.id().equals(10L)).findFirst().orElseThrow();
        ListingSummaryResponse activeSummary = responses.stream()
                .filter(r -> r.id().equals(20L)).findFirst().orElseThrow();
        assertThat(tradingSummary.tradeId()).isEqualTo(500L);
        assertThat(activeSummary.tradeId()).isNull();
    }
}
