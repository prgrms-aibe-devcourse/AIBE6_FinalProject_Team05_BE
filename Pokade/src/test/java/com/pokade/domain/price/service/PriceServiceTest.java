package com.pokade.domain.price.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Mock
    private PriceTradeStatsRepository priceTradeStatsRepository;

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

    @Test
    @DisplayName("t5 여러 카드를 배치 조회하면 카드별 대표 판본 기준 가격을 반환하고, "
            + "대표 판본이 없는 카드는 가격을 null로 채운다")
    void t5() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L, 2L, 3L)))
                .willReturn(List.of(
                        primaryVariantIdView(1L, 10L),
                        primaryVariantIdView(2L, 20L)
                        // 3L은 대표 판본이 없는 카드로 취급(응답에서 variantId 없음)
                ));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L, 20L), ListingStatus.ACTIVE, null))
                .willReturn(List.of(listingPriceView(10L, 3000000)));
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L, 20L)))
                .willReturn(List.of(buyOfferPriceView(20L, 2000000)));

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L, 2L, 3L), null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(new CardPriceSummaryResponse(1L, 3000000, null, "KRW"));
        assertThat(result.get(1)).isEqualTo(new CardPriceSummaryResponse(2L, null, 2000000, "KRW"));
        assertThat(result.get(2)).isEqualTo(new CardPriceSummaryResponse(3L, null, null, "KRW"));
    }

    @Test
    @DisplayName("t6 cardIds가 비어 있으면 INVALID_INPUT 예외가 발생한다")
    void t6() {
        assertThatThrownBy(() -> priceService.getSummaries(List.of(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t7 cardIds가 상한(100개)을 넘으면 INVALID_INPUT 예외가 발생한다")
    void t7() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> priceService.getSummaries(tooMany, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t8 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생하고 거래 리포지토리를 조회하지 않는다")
    void t8() {
        given(cardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> priceService.getStats(999L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(priceTradeStatsRepository, never()).countCompletedTradesByGradeSince(any(), any(), any(), any());
    }

    @Test
    @DisplayName("t9 variantId 미지정이고 대표 변형이 없으면 PRIMARY_VARIANT_NOT_FOUND 예외가 발생한다")
    void t9() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> priceService.getStats(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRIMARY_VARIANT_NOT_FOUND);
    }

    @Test
    @DisplayName("t10 S등급 체결 이력이 전혀 없으면 등락률 0, 거래량 0을 반환한다")
    void t10() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceTradeStatsRepository.countCompletedTradesByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(0L);
        given(priceTradeStatsRepository.findAveragePriceByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(null);
        given(priceTradeStatsRepository.findAveragePriceByGradeBetween(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(null);

        PriceStatsResponse result = priceService.getStats(1L, 10L);

        assertThat(result.changeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.volume()).isZero();
    }

    @Test
    @DisplayName("t11 최근 7일 체결은 있지만 그 이전 블록에 체결 이력이 없으면 등락률 0, 거래량은 실제 건수를 반환한다")
    void t11() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceTradeStatsRepository.countCompletedTradesByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(1L);
        given(priceTradeStatsRepository.findAveragePriceByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(3000000.0);
        given(priceTradeStatsRepository.findAveragePriceByGradeBetween(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(null);

        PriceStatsResponse result = priceService.getStats(1L, 10L);

        assertThat(result.changeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.volume()).isEqualTo(1L);
    }

    @Test
    @DisplayName("t12 최근 7일 평균가와 그 이전 7일 평균가가 모두 있으면 블록 간 등락률을 계산해 반환한다")
    void t12() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceTradeStatsRepository.countCompletedTradesByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(4L);
        given(priceTradeStatsRepository.findAveragePriceByGradeSince(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(3000000.0);
        given(priceTradeStatsRepository.findAveragePriceByGradeBetween(eq(1L), eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(2830000.0);

        PriceStatsResponse result = priceService.getStats(1L, 10L);

        // (3,000,000 - 2,830,000) / 2,830,000 * 100 ≈ 6.01
        assertThat(result.changeRate()).isEqualByComparingTo(new BigDecimal("6.01"));
        assertThat(result.volume()).isEqualTo(4L);
    }

    @Test
    @DisplayName("t13 grade를 지정하면 해당 등급의 활성 매물 중 최저가만 반환한다")
    void t13() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, ListingGrade.S))
                .willReturn(List.of(listingPriceView(10L, 3000000)));
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), ListingGrade.S);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, 3000000, null, "KRW"));
    }

    @Test
    @DisplayName("t14 grade를 지정했지만 해당 등급의 활성 매물이 없는 카드는 buyPrice를 null로 반환한다")
    void t14() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, ListingGrade.A))
                .willReturn(List.of());
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), ListingGrade.A);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, null, null, "KRW"));
    }

    private CardVariantRepository.PrimaryVariantIdView primaryVariantIdView(Long cardId, Long variantId) {
        return new CardVariantRepository.PrimaryVariantIdView() {
            @Override
            public Long getCardId() {
                return cardId;
            }

            @Override
            public Long getVariantId() {
                return variantId;
            }
        };
    }

    private ListingRepository.VariantPriceView listingPriceView(Long variantId, Integer price) {
        return new ListingRepository.VariantPriceView() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public Integer getPrice() {
                return price;
            }
        };
    }

    private BuyOfferRepository.VariantPriceView buyOfferPriceView(Long variantId, Integer price) {
        return new BuyOfferRepository.VariantPriceView() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public Integer getPrice() {
                return price;
            }
        };
    }
}
