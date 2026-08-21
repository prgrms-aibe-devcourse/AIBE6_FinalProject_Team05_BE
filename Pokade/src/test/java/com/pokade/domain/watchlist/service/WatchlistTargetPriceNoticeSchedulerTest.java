package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

// 이 클래스는 이제 후보를 모아 WatchlistTargetPriceNoticeProcessor(별도 빈, REQUIRES_NEW)에 위임하는
// 역할만 한다 - 실제 목표가 판정/알림 생성/권한 선점 로직은 WatchlistTargetPriceNoticeProcessorTest에서 검증한다.
@ExtendWith(MockitoExtension.class)
class WatchlistTargetPriceNoticeSchedulerTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock CardRepository cardRepository;
    @Mock PriceTradeStatsRepository priceTradeStatsRepository;
    @Mock WatchlistTargetPriceNoticeProcessor processor;
    @InjectMocks WatchlistTargetPriceNoticeScheduler scheduler;

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    @Test
    @DisplayName("워치리스트가 0건이면 추가 조회 없이 정상 종료된다")
    void detect_noWatchlists_doesNothing() {
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of());

        scheduler.detectTargetPriceReached();

        then(cardRepository).should(never()).findAllById(any());
        then(priceTradeStatsRepository).should(never()).findPriceRangesByCardIds(any(), any(), any());
        then(processor).should(never()).process(any(), any(), any());
    }

    @Test
    @DisplayName("후보 각각에 대해 카드/전체기간 범위와 함께 processor.process()를 위임 호출한다")
    void detect_delegatesEachCandidateToProcessor() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of(watchlist));
        Card card = Card.builder().id(10L).name("리자몽").build();
        given(cardRepository.findAllById(List.of(10L))).willReturn(List.of(card));

        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(allTimeRange));

        scheduler.detectTargetPriceReached();

        then(processor).should().process(watchlist.getId(), card, allTimeRange);
    }

    @Test
    @DisplayName("배치 중 한 항목의 processor.process()가 예외를 던져도 나머지 항목은 정상 위임된다")
    void detect_oneItemFails_othersStillDelegated() {
        Watchlist failing = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        Watchlist succeeding = Watchlist.builder().userId(2L).cardId(20L).targetBuyPrice(2000).build();
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of(failing, succeeding));
        Card failingCard = Card.builder().id(10L).name("리자몽").build();
        Card succeedingCard = Card.builder().id(20L).name("이브이").build();
        given(cardRepository.findAllById(List.of(10L, 20L))).willReturn(List.of(failingCard, succeedingCard));

        PriceRange failingRange = new PriceRange(10L, 800, 1200);
        PriceRange succeedingRange = new PriceRange(20L, 1800, 2200);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(eq(List.of(10L, 20L)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(failingRange, succeedingRange));

        doThrow(new RuntimeException("처리 중 오류")).when(processor).process(failing.getId(), failingCard, failingRange);

        scheduler.detectTargetPriceReached();

        then(processor).should().process(failing.getId(), failingCard, failingRange);
        then(processor).should().process(succeeding.getId(), succeedingCard, succeedingRange);
    }
}
