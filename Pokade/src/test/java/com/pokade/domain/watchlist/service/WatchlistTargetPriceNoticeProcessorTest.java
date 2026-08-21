package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.notification.service.NotificationService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WatchlistTargetPriceNoticeProcessorTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock PriceTradeStatsRepository priceTradeStatsRepository;
    @Mock NotificationService notificationService;
    @Mock WatchlistTargetPriceEvaluator watchlistTargetPriceEvaluator;
    @InjectMocks WatchlistTargetPriceNoticeProcessor processor;

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    @Test
    @DisplayName("워치리스트가 그 사이 삭제되어 재조회에 실패하면(0건) 아무 것도 하지 않고 스킵한다")
    void process_watchlistNotFoundOnRefetch_skipsSafely() {
        given(watchlistRepository.findById(1L)).willReturn(Optional.empty());

        processor.process(1L, Card.builder().id(10L).name("리자몽").build(), new PriceRange(10L, 800, 1200));

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any(), any());
        then(watchlistRepository).should(never()).markAsNotifiedIfNotYet(any());
    }

    @Test
    @DisplayName("목표가 도달: 재조회 후 알림 생성 권한(원자적 UPDATE)을 확보한 뒤 알림을 생성한다")
    void process_targetReached_claimsAndCreatesNotification() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findById(1L)).willReturn(Optional.of(watchlist));
        Card card = Card.builder().id(10L).name("리자몽").build();

        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, allTimeRange)).willReturn(1000);

        PriceRange sinceRegistration = new PriceRange(10L, 900, 1100);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of(sinceRegistration));
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, sinceRegistration)).willReturn(1000);
        given(watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId())).willReturn(1);
        given(watchlistTargetPriceEvaluator.resolveCardDisplayName(card)).willReturn("리자몽");

        processor.process(1L, card, allTimeRange);

        then(watchlistRepository).should().markAsNotifiedIfNotYet(watchlist.getId());
        then(notificationService).should().createPriceTargetNotification(watchlist, "리자몽", card, 1000);
    }

    @Test
    @DisplayName("알림 생성 권한 확보(원자적 UPDATE)가 0건이면(이미 처리됨 또는 삭제됨) 알림을 생성하지 않고 안전하게 스킵한다")
    void process_claimFails_skipsSafely() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findById(1L)).willReturn(Optional.of(watchlist));
        Card card = Card.builder().id(10L).name("리자몽").build();

        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, allTimeRange)).willReturn(1000);

        PriceRange sinceRegistration = new PriceRange(10L, 900, 1100);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of(sinceRegistration));
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, sinceRegistration)).willReturn(1000);
        given(watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId())).willReturn(0);

        processor.process(1L, card, allTimeRange);

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("등록 이전 체결로만 목표가 근처였던 경우, 등록 이후 기준 재확인에서 걸러져 알림이 가지 않는다")
    void process_reachedOnlyBeforeRegistration_noNotification() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findById(1L)).willReturn(Optional.of(watchlist));
        Card card = Card.builder().id(10L).name("리자몽").build();

        // 전체 기간 기준으로는 후보로 걸러짐(1차 통과)
        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, allTimeRange)).willReturn(1000);

        // 등록 이후로 좁히면 체결 이력 없음 -> 2차 확인에서 탈락
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of());
        given(watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, null)).willReturn(null);

        processor.process(1L, card, allTimeRange);

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any(), any());
        then(watchlistRepository).should(never()).markAsNotifiedIfNotYet(any());
    }

    @Test
    @DisplayName("카드 정보가 없으면(배치 조회에서 못 찾은 경우) 알림을 생성하지 않는다")
    void process_cardNull_noNotification() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findById(1L)).willReturn(Optional.of(watchlist));

        processor.process(1L, null, new PriceRange(10L, 800, 1200));

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any(), any());
        then(watchlistRepository).should(never()).markAsNotifiedIfNotYet(any());
    }
}
