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
import com.pokade.domain.price.dto.CardPricePointResponse;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.repository.PriceCardStatsRepository;
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
    private TradeRepository tradeRepository;

    @Mock
    private PriceTradeStatsRepository priceTradeStatsRepository;

    @Mock
    private PriceCardStatsRepository priceCardStatsRepository;

    @Mock
    private CardPriceRepository cardPriceRepository;

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
}
