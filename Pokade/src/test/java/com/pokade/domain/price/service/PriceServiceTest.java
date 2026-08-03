package com.pokade.domain.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private BuyOfferRepository buyOfferRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private PriceService priceService;

    @Test
    @DisplayName("t1 체결 이력이 있으면 오래된순으로 정렬된 차트 데이터를 반환한다")
    void t1() {
        Listing listing = Listing.builder().cardId(1L).sellerId(1L).price(3000000).grade(ListingGrade.S).build();
        Trade trade = Trade.builder().listing(listing).buyerId(2L).price(3000000).build();
        given(cardRepository.existsById(1L)).willReturn(true);
        given(tradeRepository.findCompletedTradesSince(eq(1L), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(trade));

        List<TradeSummaryResponse> result = priceService.getPriceChart(1L, "30d");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).price()).isEqualTo(3000000);
        assertThat(result.get(0).grade()).isEqualTo(ListingGrade.S);
    }

    @Test
    @DisplayName("t2 체결 이력이 없으면 빈 목록을 반환한다")
    void t2() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(tradeRepository.findCompletedTradesSince(eq(1L), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of());

        List<TradeSummaryResponse> result = priceService.getPriceChart(1L, "90d");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t3 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생하고 리포지토리를 조회하지 않는다")
    void t3() {
        given(cardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> priceService.getPriceChart(999L, "1y"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(tradeRepository, never()).findCompletedTradesSince(any(), any(), any());
    }

    @Test
    @DisplayName("t4 잘못된 period 값이면 INVALID_PERIOD 예외가 발생하고 리포지토리를 조회하지 않는다")
    void t4() {
        given(cardRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> priceService.getPriceChart(1L, "7d"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        verify(tradeRepository, never()).findCompletedTradesSince(any(), any(), any());
    }
}
