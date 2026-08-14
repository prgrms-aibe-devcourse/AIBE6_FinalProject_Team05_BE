package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WatchlistTargetPriceNoticeServiceTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock CardRepository cardRepository;
    @Mock PriceTradeStatsRepository priceTradeStatsRepository;
    @Mock NotificationService notificationService;
    @Mock WatchlistService watchlistService;
    @InjectMocks WatchlistTargetPriceNoticeService noticeService;

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

        noticeService.detectTargetPriceReached();

        then(cardRepository).should(never()).findAllById(any());
        then(priceTradeStatsRepository).should(never())
                .findPriceRangesByCardIds(any(), any(), any());
        then(notificationService).should(never())
                .createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("목표가 도달: 알림 생성 후 isNotified가 true로 갱신된다")
    void detect_targetReached_createsNotificationAndMarksNotified() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of(watchlist));
        given(cardRepository.findAllById(List.of(10L)))
                .willReturn(List.of(Card.builder().id(10L).name("리자몽").build()));

        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(allTimeRange));
        given(watchlistService.resolveReachedTargetPrice(watchlist, allTimeRange)).willReturn(1000);

        PriceRange sinceRegistration = new PriceRange(10L, 900, 1100);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of(sinceRegistration));
        given(watchlistService.resolveReachedTargetPrice(watchlist, sinceRegistration)).willReturn(1000);

        noticeService.detectTargetPriceReached();

        then(notificationService).should().createPriceTargetNotification(watchlist, "리자몽", 1000);
        assertThat(watchlist.isNotified()).isTrue();
    }

    @Test
    @DisplayName("등록 이전 체결로만 목표가 근처였던 경우, 등록 이후 기준 재확인에서 걸러져 알림이 가지 않는다")
    void detect_reachedOnlyBeforeRegistration_noNotification() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000)
                .build();
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of(watchlist));
        given(cardRepository.findAllById(List.of(10L)))
                .willReturn(List.of(Card.builder().id(10L).name("리자몽").build()));

        // 전체 기간 기준으로는 후보로 걸러짐(1차 통과)
        PriceRange allTimeRange = new PriceRange(10L, 800, 1200);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(allTimeRange));
        given(watchlistService.resolveReachedTargetPrice(watchlist, allTimeRange)).willReturn(1000);

        // 등록 이후로 좁히면 체결 이력 없음 -> 2차 확인에서 탈락
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(10L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of());
        given(watchlistService.resolveReachedTargetPrice(watchlist, null)).willReturn(null);

        noticeService.detectTargetPriceReached();

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
        assertThat(watchlist.isNotified()).isFalse();
    }

    @Test
    @DisplayName("배치 중 한 항목이 예외를 던져도 나머지 항목은 정상 처리된다")
    void detect_oneItemFails_othersStillProcessed() {
        Watchlist failing = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(1000).build();
        Watchlist succeeding = Watchlist.builder().userId(2L).cardId(20L).targetBuyPrice(2000).build();
        given(watchlistRepository.findByIsNotifiedFalse()).willReturn(List.of(failing, succeeding));
        given(cardRepository.findAllById(List.of(10L, 20L))).willReturn(List.of(
                Card.builder().id(10L).name("리자몽").build(),
                Card.builder().id(20L).name("이브이").build()));

        PriceRange failingRange = new PriceRange(10L, 800, 1200);
        PriceRange succeedingRange = new PriceRange(20L, 1800, 2200);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(eq(List.of(10L, 20L)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(failingRange, succeedingRange));
        given(watchlistService.resolveReachedTargetPrice(failing, failingRange))
                .willThrow(new RuntimeException("판정 중 오류"));
        given(watchlistService.resolveReachedTargetPrice(succeeding, succeedingRange)).willReturn(2000);

        PriceRange succeedingSince = new PriceRange(20L, 1900, 2100);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                eq(List.of(20L)), isNull(), eq(TradeStatus.COMPLETED), any()))
                .willReturn(List.of(succeedingSince));
        given(watchlistService.resolveReachedTargetPrice(succeeding, succeedingSince)).willReturn(2000);

        noticeService.detectTargetPriceReached();

        then(notificationService).should().createPriceTargetNotification(succeeding, "이브이", 2000);
        then(notificationService).should(never()).createPriceTargetNotification(eq(failing), any(), any());
        assertThat(succeeding.isNotified()).isTrue();
        assertThat(failing.isNotified()).isFalse();
    }
}
