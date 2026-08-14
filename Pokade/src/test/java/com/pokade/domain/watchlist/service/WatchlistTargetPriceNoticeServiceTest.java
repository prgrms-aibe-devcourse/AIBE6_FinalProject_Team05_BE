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
import static org.mockito.Mockito.times;

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
    @DisplayName("목표가 도달: 알림 생성 권한(원자적 UPDATE)을 확보한 뒤 알림을 생성한다")
    void detect_targetReached_claimsAndCreatesNotification() {
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
        given(watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId())).willReturn(1);

        noticeService.detectTargetPriceReached();

        then(watchlistRepository).should().markAsNotifiedIfNotYet(watchlist.getId());
        then(notificationService).should().createPriceTargetNotification(watchlist, "리자몽", 1000);
    }

    @Test
    @DisplayName("알림 생성 권한 확보(원자적 UPDATE)가 0건이면(이미 처리됨 또는 삭제됨) 알림을 생성하지 않고 안전하게 스킵한다")
    void detect_claimFails_skipsSafely() {
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
        // 목표가는 도달했지만, 알림 생성 직전 원자적 권한 확보에 실패한 상태(삭제됐거나 다른 인스턴스가 선점)를 시뮬레이션
        given(watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId())).willReturn(0);

        noticeService.detectTargetPriceReached();

        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
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
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);

        noticeService.detectTargetPriceReached();

        // failing/succeeding 둘 다 빌더로 만들어 getId()가 null이라 인자로는 구분할 수 없으므로,
        // "권한 확보 시도가 정확히 1번만"(= 예외를 던진 failing은 그 앞 단계에서 걸러졌다는 뜻) 호출 횟수로 검증한다.
        then(watchlistRepository).should(times(1)).markAsNotifiedIfNotYet(any());
        then(notificationService).should().createPriceTargetNotification(succeeding, "이브이", 2000);
        then(notificationService).should(never()).createPriceTargetNotification(eq(failing), any(), any());
    }
}
