package com.pokade.domain.price.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardPrice;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.price.dto.BuyOfferFulfillRequest;
import com.pokade.domain.price.dto.BuyOfferOrderbookEntryResponse;
import com.pokade.domain.price.dto.BuyOfferPaymentConfirmRequest;
import com.pokade.domain.price.dto.BuyOfferReadyRequest;
import com.pokade.domain.price.dto.BuyOfferReadyResponse;
import com.pokade.domain.price.dto.BuyOfferResponse;
import com.pokade.domain.price.dto.CardPricePointResponse;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.MarketOverviewResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.entity.BuyOffer;
import com.pokade.domain.price.entity.BuyOfferOrder;
import com.pokade.domain.price.entity.BuyOfferOrderStatus;
import com.pokade.domain.price.repository.BuyOfferOrderRepository;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.repository.PriceCardStatsRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private BuyOfferOrderRepository buyOfferOrderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private PriceTradeStatsRepository priceTradeStatsRepository;

    @Mock
    private PriceCardStatsRepository priceCardStatsRepository;

    @Mock
    private CardPriceRepository cardPriceRepository;

    @Mock
    private UserAccessChecker userAccessChecker;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PointService pointService;

    @Mock
    private TradeService tradeService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

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

        assertThatThrownBy(() -> priceService.getPriceChart(999L, "180d"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(tradeRepository, never()).findCompletedTradesSince(any(), any(), any());
    }

    @Test
    @DisplayName("t4 잘못된 period 값이면 INVALID_PERIOD 예외가 발생하고 리포지토리를 조회하지 않는다")
    void t4() {
        given(cardRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> priceService.getPriceChart(1L, "1y"))
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

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L, 2L, 3L), null, false);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW", null, null));
        assertThat(result.get(1)).isEqualTo(new CardPriceSummaryResponse(2L, null, 2000000, null, "KRW", null, null));
        assertThat(result.get(2)).isEqualTo(new CardPriceSummaryResponse(3L, null, null, null, "KRW", null, null));
    }

    @Test
    @DisplayName("t6 cardIds가 비어 있으면 INVALID_INPUT 예외가 발생한다")
    void t6() {
        assertThatThrownBy(() -> priceService.getSummaries(List.of(), null, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t7 cardIds가 상한(100개)을 넘으면 INVALID_INPUT 예외가 발생한다")
    void t7() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> priceService.getSummaries(tooMany, null, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t8 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생하고 거래 리포지토리를 조회하지 않는다")
    void t8() {
        given(cardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> priceService.getStats(999L, null, null, null))
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

        assertThatThrownBy(() -> priceService.getStats(1L, null, null, null))
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

        PriceStatsResponse result = priceService.getStats(1L, 10L, null, null);

        assertThat(result.changeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.changeAmount()).isZero();
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

        PriceStatsResponse result = priceService.getStats(1L, 10L, null, null);

        assertThat(result.changeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.changeAmount()).isZero();
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

        PriceStatsResponse result = priceService.getStats(1L, 10L, null, null);

        // (3,000,000 - 2,830,000) / 2,830,000 * 100 ≈ 6.01
        assertThat(result.changeRate()).isEqualByComparingTo(new BigDecimal("6.01"));
        assertThat(result.changeAmount()).isEqualTo(170000L);
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

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), ListingGrade.S, false);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW", null, null));
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

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), ListingGrade.A, false);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, null, null, null, "KRW", null, null));
    }

    @Test
    @DisplayName("t15 includeRecentTradePrice가 true면 최근 체결가를 함께 반환한다")
    void t15() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, null))
                .willReturn(List.of());
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());
        given(priceTradeStatsRepository.findRecentCompletedTradePricesByCardIds(List.of(1L), null, TradeStatus.COMPLETED))
                .willReturn(List.of(cardPriceView(1L, 2950000)));

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), null, true);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, null, null, 2950000, "KRW", null, null));
    }

    @Test
    @DisplayName("t16 includeRecentTradePrice가 false면 최근 체결가 조회를 하지 않는다")
    void t16() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, null))
                .willReturn(List.of(listingPriceView(10L, 3000000)));
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), null, false);

        assertThat(result).containsExactly(new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW", null, null));
        verify(priceTradeStatsRepository, never()).findRecentCompletedTradePricesByCardIds(any(), any(), any());
    }

    @Test
    @DisplayName("t31 buyPrice와 recentTradePrice가 둘 다 없으면 card_prices의 비등급(raw) 시세를 fallback으로 반환한다")
    void t31() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, ListingGrade.S))
                .willReturn(List.of());
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());
        given(priceTradeStatsRepository.findRecentCompletedTradePricesByCardIds(List.of(1L), ListingGrade.S, TradeStatus.COMPLETED))
                .willReturn(List.of());
        given(cardPriceRepository.findMarketPricesByVariantIds(List.of(10L), "raw", "", ""))
                .willReturn(List.of(variantMarketPriceView(10L, new BigDecimal("25.60"), "USD")));

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), ListingGrade.S, true);

        assertThat(result).containsExactly(
                new CardPriceSummaryResponse(1L, null, null, null, "KRW", new BigDecimal("25.60"), "USD"));
    }

    @Test
    @DisplayName("t32 buyPrice가 있으면 card_prices raw 시세를 조회하더라도 marketPrice는 무시되지 않고 함께 채워진다")
    void t32() {
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(primaryVariantIdView(1L, 10L)));
        given(listingRepository.findLowestActivePricesByVariantIds(List.of(10L), ListingStatus.ACTIVE, null))
                .willReturn(List.of(listingPriceView(10L, 3000000)));
        given(buyOfferRepository.findHighestActivePricesByVariantIds(List.of(10L)))
                .willReturn(List.of());
        given(cardPriceRepository.findMarketPricesByVariantIds(List.of(10L), "raw", "", ""))
                .willReturn(List.of(variantMarketPriceView(10L, new BigDecimal("25.60"), "USD")));

        List<CardPriceSummaryResponse> result = priceService.getSummaries(List.of(1L), null, false);

        assertThat(result).containsExactly(
                new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW", new BigDecimal("25.60"), "USD"));
    }

    private CardPriceRepository.VariantMarketPriceView variantMarketPriceView(Long variantId, BigDecimal market, String currency) {
        return new CardPriceRepository.VariantMarketPriceView() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public BigDecimal getMarket() {
                return market;
            }

            @Override
            public String getCurrency() {
                return currency;
            }

            @Override
            public BigDecimal getChange1dPct() {
                return null;
            }
        };
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

    private PriceTradeStatsRepository.CardPriceView cardPriceView(Long cardId, Integer price) {
        return new PriceTradeStatsRepository.CardPriceView() {
            @Override
            public Long getCardId() {
                return cardId;
            }

            @Override
            public Integer getPrice() {
                return price;
            }
        };
    }

    private PriceTradeStatsRepository.CardAvgPriceView cardAvgPriceView(Long cardId, Double avgPrice) {
        return new PriceTradeStatsRepository.CardAvgPriceView() {
            @Override
            public Long getCardId() {
                return cardId;
            }

            @Override
            public Double getAvgPrice() {
                return avgPrice;
            }
        };
    }

    @Test
    @DisplayName("t17 type이 rise면 두 블록 모두 체결이 있는 카드의 등락률을 내림차순으로 상위 10개 반환한다")
    void t17() {
        given(priceTradeStatsRepository.findAveragePricesByGradeSince(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(cardAvgPriceView(1L, 900000.0), cardAvgPriceView(2L, 340000.0)));
        given(priceTradeStatsRepository.findAveragePricesByGradeBetween(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(cardAvgPriceView(1L, 800000.0), cardAvgPriceView(2L, 300000.0)));
        given(cardRepository.findAllById(any()))
                .willReturn(List.of(
                        Card.builder().id(1L).name("Blastoise").build(),
                        Card.builder().id(2L).name("Charizard-GX").build()
                ));

        List<PriceRankingResponse> result = priceService.getRanking("rise");

        assertThat(result).hasSize(2);
        // (900000-800000)/800000*100 = 12.5, (340000-300000)/300000*100 ≈ 13.33 → 13.33이 먼저
        assertThat(result.get(0).changeRate()).isEqualByComparingTo("13.33");
        assertThat(result.get(1).changeRate()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("t18 type이 fall이면 등락률을 오름차순으로 상위 10개 반환한다")
    void t18() {
        given(priceTradeStatsRepository.findAveragePricesByGradeSince(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(cardAvgPriceView(1L, 430000.0)));
        given(priceTradeStatsRepository.findAveragePricesByGradeBetween(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(cardAvgPriceView(1L, 500000.0)));
        given(cardRepository.findAllById(any())).willReturn(List.of(Card.builder().id(1L).name("Charizard ex").build()));

        List<PriceRankingResponse> result = priceService.getRanking("fall");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cardName()).isEqualTo("Charizard ex");
        assertThat(result.get(0).changeRate()).isEqualByComparingTo("-14");
    }

    @Test
    @DisplayName("t19 잘못된 type 값이면 INVALID_RANKING_TYPE 예외가 발생하고 리포지토리를 조회하지 않는다")
    void t19() {
        assertThatThrownBy(() -> priceService.getRanking("up"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RANKING_TYPE);
        verifyNoInteractions(priceTradeStatsRepository);
    }

    @Test
    @DisplayName("t20 최근/이전 블록 모두에 체결이 있는 카드가 없으면 빈 목록을 반환하고 카드 조회를 하지 않는다")
    void t20() {
        given(priceTradeStatsRepository.findAveragePricesByGradeSince(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(cardAvgPriceView(1L, 900000.0)));
        given(priceTradeStatsRepository.findAveragePricesByGradeBetween(eq(ListingGrade.S), eq(TradeStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of());

        List<PriceRankingResponse> result = priceService.getRanking("rise");

        assertThat(result).isEmpty();
        verify(cardRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("t21 grade와 period를 지정하면 card_prices에서 해당 조합의 등락률을 조회해 반환한다")
    void t21() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(10L, "10", "PSA", "30d"))
                .willReturn(java.util.Optional.of(cardPriceChangeView(new BigDecimal("5.50"), new BigDecimal("12000"))));

        PriceStatsResponse result = priceService.getStats(1L, 10L, ListingGrade.PSA10, "30d");

        assertThat(result.changeRate()).isEqualByComparingTo("5.50");
        // change_7d_amount 컬럼은 7일치만 있어 30일 조회에서는 금액을 알 수 없다 - null이어야 한다.
        assertThat(result.changeAmount()).isNull();
        assertThat(result.volume()).isZero();
    }

    @Test
    @DisplayName("t22 grade만 지정하고 period를 지정하지 않으면 기본값 7d로 조회하고, change_7d_amount를 그대로 반환한다")
    void t22() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(10L, "S", "", "7d"))
                .willReturn(java.util.Optional.of(cardPriceChangeView(new BigDecimal("-2.71"), new BigDecimal("-4.86"))));

        PriceStatsResponse result = priceService.getStats(1L, 10L, ListingGrade.S, null);

        assertThat(result.changeRate()).isEqualByComparingTo("-2.71");
        assertThat(result.changeAmount()).isEqualTo(-5L);
        assertThat(result.volume()).isZero();
    }

    @Test
    @DisplayName("t23 card_prices에 해당 variant/grade 조합 데이터가 없으면 등락률 0, 금액 null을 반환한다")
    void t23() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(10L, "B", "", "7d"))
                .willReturn(java.util.Optional.empty());

        PriceStatsResponse result = priceService.getStats(1L, 10L, ListingGrade.B, "7d");

        assertThat(result.changeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.changeAmount()).isNull();
        assertThat(result.volume()).isZero();
    }

    @Test
    @DisplayName("t24 period 값이 잘못되면 INVALID_PERIOD 예외가 발생하고 card_prices를 조회하지 않는다")
    void t24() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.of(10L));

        assertThatThrownBy(() -> priceService.getStats(1L, null, ListingGrade.S, "3d"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        verify(priceCardStatsRepository, never()).findChangeByVariantGradeCompanyAndPeriod(any(), any(), any(), any());
    }

    @Test
    @DisplayName("t25 period만 지정하고 grade를 지정하지 않으면 기본 등급 S로 조회한다")
    void t25() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(10L, "S", "", "14d"))
                .willReturn(java.util.Optional.of(cardPriceChangeView(new BigDecimal("1.20"), null)));

        PriceStatsResponse result = priceService.getStats(1L, 10L, null, "14d");

        assertThat(result.changeRate()).isEqualByComparingTo("1.20");
        assertThat(result.changeAmount()).isNull();
    }

    @Test
    @DisplayName("t33 구매입찰 호가창을 조회하면 리포지토리가 반환한 순서 그대로 응답으로 변환한다")
    void t33() {
        given(cardRepository.existsById(1L)).willReturn(true);
        BuyOffer highest = BuyOffer.builder().id(1L).cardId(1L).variantId(10L).price(3100000).grade(ListingGrade.S).build();
        BuyOffer lowest = BuyOffer.builder().id(2L).cardId(1L).variantId(10L).price(2700000).grade(null).build();
        given(buyOfferRepository.findOrderbook(1L, 10L, null)).willReturn(List.of(highest, lowest));

        List<BuyOfferOrderbookEntryResponse> result = priceService.getBuyOfferOrderbook(1L, 10L, null);

        assertThat(result).containsExactly(
                new BuyOfferOrderbookEntryResponse(1L, 3100000, ListingGrade.S),
                new BuyOfferOrderbookEntryResponse(2L, 2700000, null));
    }

    @Test
    @DisplayName("t34 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생하고 리포지토리를 조회하지 않는다")
    void t34() {
        given(cardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> priceService.getBuyOfferOrderbook(999L, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(buyOfferRepository, never()).findOrderbook(any(), any(), any());
    }

    @Test
    @DisplayName("t35 variantId를 지정하지 않으면 대표 변형을 조회해 사용한다")
    void t35() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.of(10L));
        given(buyOfferRepository.findOrderbook(1L, 10L, ListingGrade.PSA10)).willReturn(List.of());

        List<BuyOfferOrderbookEntryResponse> result = priceService.getBuyOfferOrderbook(1L, null, ListingGrade.PSA10);

        assertThat(result).isEmpty();
        verify(buyOfferRepository).findOrderbook(1L, 10L, ListingGrade.PSA10);
    }

    @Test
    @DisplayName("t36 variantId 미지정이고 대표 변형이 없으면 PRIMARY_VARIANT_NOT_FOUND 예외가 발생한다")
    void t36() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> priceService.getBuyOfferOrderbook(1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRIMARY_VARIANT_NOT_FOUND);
        verify(buyOfferRepository, never()).findOrderbook(any(), any(), any());
    }

    private BuyOfferReadyRequest buyOfferReadyRequestOf(Long cardId, Long variantId, Integer price, ListingGrade grade) {
        return buyOfferReadyRequestOf(cardId, variantId, price, grade, 0);
    }

    private BuyOfferReadyRequest buyOfferReadyRequestOf(
            Long cardId, Long variantId, Integer price, ListingGrade grade, int pointsToUse) {
        return new BuyOfferReadyRequest(
                cardId, variantId, price, grade, pointsToUse, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");
    }

    private BuyOfferOrder pendingBuyOfferOrderOf(Long buyerId, Long cardId, Long variantId, int price) {
        return BuyOfferOrder.builder()
                .orderId("bo-order-1")
                .buyerId(buyerId)
                .cardId(cardId)
                .variantId(variantId)
                .grade(ListingGrade.S)
                .price(price)
                .shippingFee(3000)
                .recipientName("김철수")
                .recipientPhone("010-1234-5678")
                .recipientAddress("서울시 강남구 테헤란로 1")
                .build();
    }

    @Test
    @DisplayName("t37 구매입찰 결제 준비 시 매물을 잠그지 않고 상품가+배송비를 합한 금액으로 주문만 PENDING으로 기록한다")
    void t37() {
        given(cardRepository.existsById(1L)).willReturn(true);
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, 10L, 250000, ListingGrade.S);

        BuyOfferReadyResponse response = priceService.readyBuyOffer(2L, request);

        assertThat(response.amount()).isEqualTo(253000);
        assertThat(response.orderId()).isNotBlank();
        ArgumentCaptor<BuyOfferOrder> captor = ArgumentCaptor.forClass(BuyOfferOrder.class);
        verify(buyOfferOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getBuyerId()).isEqualTo(2L);
        assertThat(captor.getValue().getVariantId()).isEqualTo(10L);
        assertThat(captor.getValue().getRecipientName()).isEqualTo("김철수");
        verify(buyOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("t38 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void t38() {
        given(cardRepository.existsById(999L)).willReturn(false);
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(999L, null, 100000, null);

        assertThatThrownBy(() -> priceService.readyBuyOffer(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(buyOfferOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("t39 variantId 미지정이고 대표 변형이 없으면 PRIMARY_VARIANT_NOT_FOUND 예외가 발생한다")
    void t39() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.empty());
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, null, 100000, null);

        assertThatThrownBy(() -> priceService.readyBuyOffer(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRIMARY_VARIANT_NOT_FOUND);
        verify(buyOfferOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("t40 variantId를 지정하지 않으면 대표 변형을 조회해 그 변형으로 주문을 기록한다")
    void t40() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.of(10L));
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, null, 100000, null);

        priceService.readyBuyOffer(3L, request);

        ArgumentCaptor<BuyOfferOrder> captor = ArgumentCaptor.forClass(BuyOfferOrder.class);
        verify(buyOfferOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getVariantId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("t41 결제승인 시 주문이 없으면 BUY_OFFER_ORDER_NOT_FOUND 예외가 발생한다")
    void t41() {
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 253000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUY_OFFER_ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("t42 결제승인 시 주문의 구매자가 아니면 ACCESS_DENIED 예외가 발생한다")
    void t42() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(999L, "pay_123", "bo-order-1", 253000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("t43 결제승인 시 이미 처리된 주문이면 BUY_OFFER_ORDER_ALREADY_PROCESSED 예외가 발생한다")
    void t43() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        order.markConfirmed();
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 253000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUY_OFFER_ORDER_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("t44 결제승인 시 금액이 다르면 INVALID_INPUT 예외가 발생하고 토스 승인을 호출하지 않는다")
    void t44() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("t45 결제승인 성공 시 구매입찰을 생성하고 주문을 CONFIRMED로 기록한다")
    void t45() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));
        given(buyOfferRepository.save(any(BuyOffer.class))).willAnswer(invocation -> invocation.getArgument(0));

        BuyOfferResponse response = priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 253000);

        assertThat(response.buyerId()).isEqualTo(2L);
        assertThat(response.recipientName()).isEqualTo("김철수");
        assertThat(order.getStatus()).isEqualTo(BuyOfferOrderStatus.CONFIRMED);
        verify(tossPaymentClient).confirmPayment("pay_123", "bo-order-1", 253000L);
        ArgumentCaptor<BuyOffer> captor = ArgumentCaptor.forClass(BuyOffer.class);
        verify(buyOfferRepository).save(captor.capture());
        assertThat(captor.getValue().getTossPaymentKey()).isEqualTo("pay_123");
    }

    @Test
    @DisplayName("t46 결제승인 실패 시 주문을 FAILED로 기록하고 예외를 다시 던진다")
    void t46() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));
        willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED))
                .given(tossPaymentClient).confirmPayment(any(), any(), any(Long.class));

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 253000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
        verify(buyOfferOrderRepository).markFailedIfPending("bo-order-1");
        verify(buyOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("t47 결제 준비 시 포인트 사용액이 결제 금액보다 크면 INVALID_INPUT 예외가 발생한다")
    void t47() {
        given(cardRepository.existsById(1L)).willReturn(true);
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, 10L, 250000, ListingGrade.S, 300000);

        assertThatThrownBy(() -> priceService.readyBuyOffer(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(buyOfferOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("t48 결제 준비 시 포인트 잔액이 부족하면 INSUFFICIENT_POINT_BALANCE 예외가 발생한다")
    void t48() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(userRepository.findById(2L)).willReturn(Optional.of(User.builder().pointBalance(1000).build()));
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, 10L, 250000, ListingGrade.S, 5000);

        assertThatThrownBy(() -> priceService.readyBuyOffer(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT_BALANCE);
        verify(buyOfferOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("t49 결제 준비 시 포인트 사용액만큼 뺀 금액을 결제 금액으로 반환하고 주문에 남긴다")
    void t49() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(userRepository.findById(2L)).willReturn(Optional.of(User.builder().pointBalance(100000).build()));
        BuyOfferReadyRequest request = buyOfferReadyRequestOf(1L, 10L, 250000, ListingGrade.S, 50000);

        BuyOfferReadyResponse response = priceService.readyBuyOffer(2L, request);

        assertThat(response.amount()).isEqualTo(203000);
        ArgumentCaptor<BuyOfferOrder> captor = ArgumentCaptor.forClass(BuyOfferOrder.class);
        verify(buyOfferOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getPointsUsed()).isEqualTo(50000);
    }

    @Test
    @DisplayName("t50 결제승인 시 사용한 포인트만큼 pointService.use()를 호출한다")
    void t50() {
        BuyOfferOrder order = BuyOfferOrder.builder()
                .orderId("bo-order-1").buyerId(2L).cardId(1L).variantId(10L).grade(ListingGrade.S)
                .price(250000).shippingFee(3000).pointsUsed(50000)
                .recipientName("김철수").recipientPhone("010-1234-5678").recipientAddress("서울시 강남구 테헤란로 1")
                .build();
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));
        given(buyOfferRepository.save(any(BuyOffer.class))).willAnswer(invocation -> invocation.getArgument(0));

        priceService.confirmBuyOfferPurchase(2L, "pay_123", "bo-order-1", 203000);

        verify(pointService).use(2L, 50000, null);
        verify(tossPaymentClient).confirmPayment("pay_123", "bo-order-1", 203000L);
    }

    @Test
    @DisplayName("t51 포인트로 전액을 충당해 결제 금액이 0이면 토스 승인 없이 바로 구매입찰을 생성한다")
    void t51() {
        BuyOfferOrder order = BuyOfferOrder.builder()
                .orderId("bo-order-1").buyerId(2L).cardId(1L).variantId(10L).grade(ListingGrade.S)
                .price(250000).shippingFee(3000).pointsUsed(253000)
                .recipientName("김철수").recipientPhone("010-1234-5678").recipientAddress("서울시 강남구 테헤란로 1")
                .build();
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));
        given(buyOfferRepository.save(any(BuyOffer.class))).willAnswer(invocation -> invocation.getArgument(0));

        BuyOfferResponse response = priceService.confirmBuyOfferPurchase(2L, null, "bo-order-1", 0);

        assertThat(order.getStatus()).isEqualTo(BuyOfferOrderStatus.CONFIRMED);
        verify(pointService).use(2L, 253000, null);
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
        ArgumentCaptor<BuyOffer> captor = ArgumentCaptor.forClass(BuyOffer.class);
        verify(buyOfferRepository).save(captor.capture());
        assertThat(captor.getValue().getTossPaymentKey()).isNull();
    }

    @Test
    @DisplayName("t52 결제 금액이 남아있는데 paymentKey가 없으면 INVALID_INPUT 예외가 발생하고 토스 승인을 호출하지 않는다")
    void t52() {
        BuyOfferOrder order = pendingBuyOfferOrderOf(2L, 1L, 10L, 250000);
        given(buyOfferOrderRepository.findByOrderId("bo-order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> priceService.confirmBuyOfferPurchase(2L, null, "bo-order-1", 253000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
        verify(pointService, never()).use(any(), anyInt(), any());
    }

    private PriceCardStatsRepository.CardPriceChangeView cardPriceChangeView(BigDecimal changePct, BigDecimal change7dAmount) {
        return new PriceCardStatsRepository.CardPriceChangeView() {
            @Override
            public BigDecimal getChangePct() {
                return changePct;
            }

            @Override
            public BigDecimal getChange7dAmount() {
                return change7dAmount;
            }
        };
    }

    @Test
    @DisplayName("t26 등락률과 market이 모두 있으면 오래된순(180d→now)으로 7개 포인트를 역산해 반환한다")
    void t26() {
        given(cardRepository.existsById(1L)).willReturn(true);
        CardPrice cardPrice = CardPrice.builder()
                .priceType("graded").grade("10").company("PSA")
                .market(new BigDecimal("1000.00"))
                .currency("USD")
                .change1dPct(new BigDecimal("0"))
                .change7dPct(new BigDecimal("25"))
                .change14dPct(new BigDecimal("100"))
                .change30dPct(new BigDecimal("-50"))
                .change90dPct(new BigDecimal("300"))
                .change180dPct(new BigDecimal("900"))
                .updatedAt(LocalDateTime.now())
                .build();
        given(cardPriceRepository.findByVariantIdAndPriceTypeAndGradeAndCompany(10L, "graded", "10", "PSA"))
                .willReturn(java.util.Optional.of(cardPrice));

        List<CardPricePointResponse> result = priceService.getGradeChart(1L, 10L, ListingGrade.PSA10);

        assertThat(result).hasSize(7);
        assertThat(result.get(0).price()).isEqualByComparingTo("100.00");   // 180d 전
        assertThat(result.get(1).price()).isEqualByComparingTo("250.00");   // 90d 전
        assertThat(result.get(2).price()).isEqualByComparingTo("2000.00"); // 30d 전
        assertThat(result.get(3).price()).isEqualByComparingTo("500.00");  // 14d 전
        assertThat(result.get(4).price()).isEqualByComparingTo("800.00");  // 7d 전
        assertThat(result.get(5).price()).isEqualByComparingTo("1000.00"); // 1d 전
        assertThat(result.get(6).price()).isEqualByComparingTo("1000.00"); // now(market)
        assertThat(result.get(0).date()).isBefore(result.get(6).date());
        assertThat(result).allMatch(p -> p.currency().equals("USD"));
    }

    @Test
    @DisplayName("t27 일부 change_*_pct가 null이면 해당 포인트는 건너뛰고 나머지만 반환한다")
    void t27() {
        given(cardRepository.existsById(1L)).willReturn(true);
        CardPrice cardPrice = CardPrice.builder()
                .priceType("graded").grade("S").company("")
                .market(new BigDecimal("500.00"))
                .currency("USD")
                .change1dPct(new BigDecimal("0"))
                .change7dPct(new BigDecimal("25"))
                .change14dPct(null)
                .change30dPct(new BigDecimal("-50"))
                .change90dPct(null)
                .change180dPct(new BigDecimal("400"))
                .updatedAt(LocalDateTime.now())
                .build();
        given(cardPriceRepository.findByVariantIdAndPriceTypeAndGradeAndCompany(10L, "graded", "S", ""))
                .willReturn(java.util.Optional.of(cardPrice));

        List<CardPricePointResponse> result = priceService.getGradeChart(1L, 10L, ListingGrade.S);

        // 180d, 30d, 7d, 1d, now = 5개 (14d, 90d는 null이라 제외)
        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("t28 card_prices에 해당 variant/grade 조합이 없으면 빈 목록을 반환한다")
    void t28() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardPriceRepository.findByVariantIdAndPriceTypeAndGradeAndCompany(10L, "graded", "A", ""))
                .willReturn(java.util.Optional.empty());

        List<CardPricePointResponse> result = priceService.getGradeChart(1L, 10L, ListingGrade.A);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t29 존재하지 않는 카드면 CARD_NOT_FOUND 예외가 발생한다")
    void t29() {
        given(cardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> priceService.getGradeChart(999L, 10L, ListingGrade.S))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verifyNoInteractions(cardPriceRepository);
    }

    @Test
    @DisplayName("t30 variantId 미지정이고 대표 변형이 없으면 PRIMARY_VARIANT_NOT_FOUND 예외가 발생한다")
    void t30() {
        given(cardRepository.existsById(1L)).willReturn(true);
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> priceService.getGradeChart(1L, null, ListingGrade.S))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRIMARY_VARIANT_NOT_FOUND);
        verifyNoInteractions(cardPriceRepository);
    }

    private BuyOffer activeBuyOfferOf(Long id, Long buyerId) {
        return BuyOffer.builder()
                .id(id)
                .cardId(1L)
                .buyerId(buyerId)
                .variantId(10L)
                .price(250000)
                .grade(ListingGrade.S)
                .recipientName("김철수")
                .recipientPhone("010-1234-5678")
                .recipientAddress("서울시 강남구")
                .tossPaymentKey("pay_999")
                .pointsUsed(1000)
                .shippingFee(3000)
                .build();
    }

    private BuyOfferFulfillRequest buyOfferFulfillRequestOf() {
        return new BuyOfferFulfillRequest(
                "국민은행", "110-1234-5678", "홍길동", "홍길동", "010-9999-8888", "서울시 서초구");
    }

    @Test
    @DisplayName("t53 즉시판매 성공 시 매물을 생성해 즉시 TRADING으로 잠그고 구매입찰을 체결 처리한다")
    void t53() {
        BuyOffer buyOffer = activeBuyOfferOf(7L, 2L);
        given(buyOfferRepository.findById(7L)).willReturn(java.util.Optional.of(buyOffer));
        given(listingRepository.save(any(Listing.class))).willAnswer(invocation -> invocation.getArgument(0));
        TradeResponse expected = new TradeResponse(
                100L, null, 2L, 1L, 1L, "리자몽", 250000, TradeStatus.PENDING,
                null, null, null, null, null,
                "김철수", "010-1234-5678", "서울시 강남구", java.time.LocalDateTime.now());
        given(tradeService.createMatchedTrade(
                any(), eq(2L), eq(250000), eq(252000), eq("김철수"), eq("010-1234-5678"),
                eq("서울시 강남구"), eq("pay_999"), eq(1000)))
                .willReturn(expected);

        TradeResponse response = priceService.fulfillBuyOffer(7L, 1L, buyOfferFulfillRequestOf());

        assertThat(response).isEqualTo(expected);
        assertThat(buyOffer.getStatus()).isEqualTo("MATCHED");
        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo(1L);
        assertThat(captor.getValue().getPrice()).isEqualTo(250000);
        assertThat(captor.getValue().getGrade()).isEqualTo(ListingGrade.S);
    }

    @Test
    @DisplayName("t54 존재하지 않는 구매입찰이면 BUY_OFFER_NOT_FOUND 예외가 발생한다")
    void t54() {
        given(buyOfferRepository.findById(999L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> priceService.fulfillBuyOffer(999L, 1L, buyOfferFulfillRequestOf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUY_OFFER_NOT_FOUND);
        verifyNoInteractions(listingRepository, tradeService);
    }

    @Test
    @DisplayName("t55 본인이 등록한 구매입찰이면 SELF_BUY_OFFER_NOT_ALLOWED 예외가 발생한다")
    void t55() {
        BuyOffer buyOffer = activeBuyOfferOf(7L, 1L);
        given(buyOfferRepository.findById(7L)).willReturn(java.util.Optional.of(buyOffer));

        assertThatThrownBy(() -> priceService.fulfillBuyOffer(7L, 1L, buyOfferFulfillRequestOf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_BUY_OFFER_NOT_ALLOWED);
        verifyNoInteractions(listingRepository, tradeService);
    }

    @Test
    @DisplayName("t56 이미 체결된 구매입찰이면 BUY_OFFER_ALREADY_MATCHED 예외가 발생하고 매물도 생성하지 않는다")
    void t56() {
        BuyOffer buyOffer = activeBuyOfferOf(7L, 2L);
        buyOffer.markMatched();
        given(buyOfferRepository.findById(7L)).willReturn(java.util.Optional.of(buyOffer));

        assertThatThrownBy(() -> priceService.fulfillBuyOffer(7L, 1L, buyOfferFulfillRequestOf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUY_OFFER_ALREADY_MATCHED);
        verifyNoInteractions(listingRepository, tradeService);
    }

    private PriceTradeStatsRepository.DailyMarketStatView dailyMarketStatView(
            LocalDate date, long volume, Double medianPrice) {
        return new PriceTradeStatsRepository.DailyMarketStatView() {
            @Override
            public LocalDate getTradeDate() {
                return date;
            }

            @Override
            public Long getVolume() {
                return volume;
            }

            @Override
            public Double getMedianPrice() {
                return medianPrice;
            }
        };
    }

    @Test
    @DisplayName("t57 오늘/어제 모두 체결이 있으면 전일 대비 거래량/중간값 변화율을 계산하고 30일치 일별 통계를 반환한다")
    void t57() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        given(priceTradeStatsRepository.findDailyMarketStats(eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(
                        dailyMarketStatView(yesterday, 10L, 3000000.0),
                        dailyMarketStatView(today, 12L, 3100000.0)
                ));

        MarketOverviewResponse result = priceService.getMarketOverview();

        assertThat(result.todayVolume()).isEqualTo(12L);
        assertThat(result.volumeChangeRate()).isEqualByComparingTo("20.00");
        assertThat(result.todayMedianPrice()).isEqualTo(3100000L);
        // (3100000-3000000)/3000000*100 = 3.3333... -> 3.33
        assertThat(result.medianChangeRate1d()).isEqualByComparingTo("3.33");
        assertThat(result.medianChangeAmount1d()).isEqualTo(100000L);
        assertThat(result.totalVolume()).isEqualTo(22L);
        assertThat(result.dailyStats()).hasSize(30);
        assertThat(result.dailyStats().get(29).date()).isEqualTo(today);
        assertThat(result.dailyStats().get(28).date()).isEqualTo(yesterday);
    }

    @Test
    @DisplayName("t58 어제 거래가 0건이면 거래량 증가율은 null이다")
    void t58() {
        LocalDate today = LocalDate.now();
        given(priceTradeStatsRepository.findDailyMarketStats(eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(dailyMarketStatView(today, 5L, 2000000.0)));

        MarketOverviewResponse result = priceService.getMarketOverview();

        assertThat(result.todayVolume()).isEqualTo(5L);
        assertThat(result.volumeChangeRate()).isNull();
        assertThat(result.medianChangeRate1d()).isNull();
        assertThat(result.medianChangeAmount1d()).isNull();
    }

    @Test
    @DisplayName("t59 오늘 체결이 전혀 없으면 오늘의 거래량/중간값이 0과 null이고 변화율도 null이다")
    void t59() {
        given(priceTradeStatsRepository.findDailyMarketStats(eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of());

        MarketOverviewResponse result = priceService.getMarketOverview();

        assertThat(result.todayVolume()).isZero();
        assertThat(result.todayMedianPrice()).isNull();
        assertThat(result.volumeChangeRate()).isNull();
        assertThat(result.medianChangeRate1d()).isNull();
        assertThat(result.medianChangeAmount1d()).isNull();
        assertThat(result.medianChangeRate7d()).isNull();
        assertThat(result.medianChangeRate30d()).isNull();
        assertThat(result.totalVolume()).isZero();
        assertThat(result.dailyStats()).hasSize(30);
    }

    @Test
    @DisplayName("t60 일주일 전/30일 전 체결이 있으면 오늘 중간값과 비교해 각 기준의 변화율을 계산한다")
    void t60() {
        LocalDate today = LocalDate.now();
        given(priceTradeStatsRepository.findDailyMarketStats(eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(
                        dailyMarketStatView(today.minusDays(30), 3L, 2000000.0),
                        dailyMarketStatView(today.minusDays(7), 4L, 2500000.0),
                        dailyMarketStatView(today, 6L, 3000000.0)
                ));

        MarketOverviewResponse result = priceService.getMarketOverview();

        // (3000000-2500000)/2500000*100 = 20.00
        assertThat(result.medianChangeRate7d()).isEqualByComparingTo("20.00");
        // (3000000-2000000)/2000000*100 = 50.00
        assertThat(result.medianChangeRate30d()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("t61 일주일 전/30일 전에 체결이 없으면 해당 기준의 변화율은 null이다")
    void t61() {
        LocalDate today = LocalDate.now();
        given(priceTradeStatsRepository.findDailyMarketStats(eq(TradeStatus.COMPLETED), any(LocalDateTime.class)))
                .willReturn(List.of(dailyMarketStatView(today, 6L, 3000000.0)));

        MarketOverviewResponse result = priceService.getMarketOverview();

        assertThat(result.medianChangeRate7d()).isNull();
        assertThat(result.medianChangeRate30d()).isNull();
    }
}
