package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

// 워치리스트 목표가(구매/판매) 도달 판정 로직의 단일 소유자. 즉시 판정 경로(WatchlistService의
// 등록/수정)와 배치 판정 경로(WatchlistTargetPriceNoticeProcessor)가 이 클래스에 공통으로 의존해,
// 두 경로가 항상 같은 기준(범위 판정, 카드 표시명)으로 알림을 생성하도록 한다.
@Service
@RequiredArgsConstructor
public class WatchlistTargetPriceEvaluator {

    private final WatchlistRepository watchlistRepository;
    private final CardRepository cardRepository;
    private final NotificationService notificationService;
    private final CardNameKoResolver cardNameKoResolver;

    // 계측 주입 규칙은 support/TestMetricsConfig javadoc 참조(#343).
    private final MeterRegistry meterRegistry;

    // 목표가에 새로 도달한 경우(아직 알림 안 간 상태에서 도달)에만 markAsNotified + 실제 알림 생성을 한다.
    // "이미 알림 갔는지"는 메모리 값이 아니라 markAsNotifiedIfNotYet()의 원자적 조건부 UPDATE(DB 기준)로
    // 판정한다 - 배치(WatchlistTargetPriceNoticeProcessor)가 그 사이 먼저 선점했을 수 있어서, 메모리에 로드된
    // isNotified만 믿으면 중복 알림이 생길 수 있다. claimed>0일 때만 엔티티도 true로 맞춰서, 호출자가 만드는
    // WatchlistResponse의 isNotified가 실제 DB 상태와 일치하게 한다(배치는 응답 DTO가 없어서 이
    // 동기화가 필요 없었던 것과 다른 점).
    public void notifyIfNewlyReached(Watchlist watchlist, Integer reachedTargetPrice) {
        if (reachedTargetPrice == null) {
            return;
        }
        int claimed = watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId());
        if (claimed == 0) {
            // 운영 계측 - #258 도입, 워치리스트/알림 대시보드가 사용 중
            meterRegistry.counter("watchlist.notify.already_claimed.calls").increment();
            return;
        }
        // 운영 계측 - #258 도입, 워치리스트/알림 대시보드가 사용 중
        meterRegistry.counter("watchlist.notify.immediate.calls").increment();
        watchlist.markAsNotified();
        notifyIfTargetAlreadyReached(watchlist, reachedTargetPrice);
    }

    private void notifyIfTargetAlreadyReached(Watchlist watchlist, Integer reachedTargetPrice) {
        cardRepository.findById(watchlist.getCardId())
                .ifPresent(card -> notificationService.createPriceTargetNotification(watchlist, resolveCardDisplayName(card), card, reachedTargetPrice));
    }

    // 알림 문구에 쓸 카드 표시명(#275) - 한글명이 있으면 한글명, 없으면(도감번호 없음/매핑 실패 등) 영문 원본으로
    // 폴백한다. 즉시 알림 경로(notifyIfTargetAlreadyReached)와 배치 알림 경로
    // (WatchlistTargetPriceNoticeProcessor)가 항상 같은 표시명을 쓰도록 이 클래스에 단일화했다.
    public String resolveCardDisplayName(Card card) {
        return Objects.requireNonNullElse(cardNameKoResolver.resolve(card), card.getName());
    }

    // createdAt은 @CreationTimestamp라 DB에 영속화된 행이면 항상 채워지지만(방금 조회한 워치리스트가
    // null일 일은 실제로는 없음), 순수 빌더로 만든 인스턴스(단위 테스트 등)나 예상 못한 레거시 데이터에
    // 대비해 방어적으로 LocalDateTime.MIN(사실상 전체 기간)으로 폴백한다.
    public LocalDateTime resolveWatchScopeStart(Watchlist watchlist) {
        return Objects.requireNonNullElse(watchlist.getCreatedAt(), LocalDateTime.MIN);
    }

    // 목표가(구매/판매) 도달 판정 - 도달한 목표가 값을 반환(없으면 null).
    // 즉시 판정 경로(WatchlistService)와 배치 판정 경로(WatchlistTargetPriceNoticeProcessor)가 공유한다.
    public Integer resolveReachedTargetPrice(Watchlist watchlist, PriceTradeStatsRepository.CardPriceRangeView range) {
        if (range == null || range.getMinPrice() == null || range.getMaxPrice() == null) {
            return null;
        }
        Integer targetBuyPrice = watchlist.getTargetBuyPrice();
        if (targetBuyPrice != null && range.getMinPrice() <= targetBuyPrice && targetBuyPrice <= range.getMaxPrice()) {
            return targetBuyPrice;
        }
        Integer targetSellPrice = watchlist.getTargetSellPrice();
        if (targetSellPrice != null && range.getMinPrice() <= targetSellPrice && targetSellPrice <= range.getMaxPrice()) {
            return targetSellPrice;
        }
        return null;
    }
}
