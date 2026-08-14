package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 워치리스트 목표가(구매/판매) 도달 감지 배치. 1시간마다 미알림 워치리스트를 훑어
// 등록(createdAt) 이후 체결분만으로 목표가 도달 여부를 재확인하고, 도달 시 알림을 남긴다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistTargetPriceNoticeService {

    private final WatchlistRepository watchlistRepository;
    private final CardRepository cardRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final NotificationService notificationService;
    private final WatchlistService watchlistService;

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void detectTargetPriceReached() {
        List<Watchlist> watchlists = watchlistRepository.findByIsNotifiedFalse();
        if (watchlists.isEmpty()) {
            return;
        }

        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, Card> cardById = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        // 1차 필터: 전체 기간 최저~최고가로 "애초에 목표가 근처도 못 간" 워치리스트를 걸러내
        // 워치리스트별 개별 조회(2차)를 후보로만 좁힌다 (N+1 최소화).
        Map<Long, PriceTradeStatsRepository.CardPriceRangeView> allTimeRangeByCardId =
                priceTradeStatsRepository.findPriceRangesByCardIds(cardIds, null, TradeStatus.COMPLETED).stream()
                        .collect(Collectors.toMap(PriceTradeStatsRepository.CardPriceRangeView::getCardId, Function.identity()));

        for (Watchlist watchlist : watchlists) {
            try {
                process(watchlist, cardById.get(watchlist.getCardId()), allTimeRangeByCardId.get(watchlist.getCardId()));
            } catch (Exception e) {
                log.warn("워치리스트 목표가 도달 감지 실패: watchlistId={}", watchlist.getId(), e);
            }
        }
    }

    private void process(Watchlist watchlist, Card card, PriceTradeStatsRepository.CardPriceRangeView allTimeRange) {
        if (card == null || watchlistService.resolveReachedTargetPrice(watchlist, allTimeRange) == null) {
            return;
        }

        // 2차 확인: 워치리스트 등록(createdAt) 이후 체결분만으로 실제 도달 여부를 재판정
        // (같은 카드를 여러 워치리스트가 서로 다른 시점에 등록했을 수 있어, 카드 단위 1차 결과만으로는 확정할 수 없음).
        List<PriceTradeStatsRepository.CardPriceRangeView> sinceRegistration = priceTradeStatsRepository
                .findPriceRangesByCardIdsSince(List.of(watchlist.getCardId()), null, TradeStatus.COMPLETED, watchlist.getCreatedAt());
        PriceTradeStatsRepository.CardPriceRangeView rangeSinceRegistration =
                sinceRegistration.isEmpty() ? null : sinceRegistration.get(0);

        Integer reachedTargetPrice = watchlistService.resolveReachedTargetPrice(watchlist, rangeSinceRegistration);
        if (reachedTargetPrice == null) {
            return;
        }

        // 알림 생성 전, 조건부 원자적 UPDATE(WHERE id=:id AND is_notified=false)로 "알림 생성 권한"을 먼저
        // 선점한다. 갱신 행 수가 0이면(그 사이 삭제됐거나 다른 트랜잭션/인스턴스가 이미 선점한 경우) 안전하게
        // 스킵한다 - existsById() 단순 존재 확인보다 더 넓은 레이스(다중 인스턴스 중복 실행 포함)를 막는다.
        int claimed = watchlistRepository.markAsNotifiedIfNotYet(watchlist.getId());
        if (claimed == 0) {
            log.info("워치리스트 알림 생성 권한을 확보하지 못해 스킵합니다(이미 처리됨 또는 삭제됨): watchlistId={}", watchlist.getId());
            return;
        }

        notificationService.createPriceTargetNotification(watchlist, card.getName(), reachedTargetPrice);
    }
}
