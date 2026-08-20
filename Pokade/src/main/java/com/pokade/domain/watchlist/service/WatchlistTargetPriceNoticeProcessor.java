package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 워치리스트 1건 처리를 별도 빈으로 분리한 이유: WatchlistTargetPriceNoticeService의 스케줄러 메서드
// 안에서 this.process(...)로 self-invocation하면 @Transactional이 AOP 프록시를 안 거쳐 전혀 적용되지
// 않는다. 여기서 REQUIRES_NEW로 워치리스트 1건마다 독립된 새 트랜잭션을 열어, 한 건 처리 중 예외가 나도
// "그 건만" 롤백되고 이전에 이미 커밋된 다른 건들은 영향받지 않도록 한다(같은 트랜잭션을 공유했다면, 참여
// 트랜잭션에서 던진 예외가 물리 트랜잭션을 rollback-only로 표시해 바깥 try/catch로 잡아도 커밋 시점에
// UnexpectedRollbackException으로 전체가 롤백되는 문제가 있었음).
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistTargetPriceNoticeProcessor {

    private final WatchlistRepository watchlistRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final NotificationService notificationService;
    private final WatchlistService watchlistService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long watchlistId, Card card, PriceTradeStatsRepository.CardPriceRangeView allTimeRange) {
        // 새 트랜잭션에서 워치리스트를 재조회한다 - 스케줄러가 후보를 읽은 시점과 이 트랜잭션이 실제로
        // 열리는 시점 사이에 삭제/변경됐을 수 있어, 이전 트랜잭션에서 로드된 인스턴스를 그대로 쓰지 않는다.
        Watchlist watchlist = watchlistRepository.findById(watchlistId).orElse(null);
        if (watchlist == null || card == null || watchlistService.resolveReachedTargetPrice(watchlist, allTimeRange) == null) {
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

        // #275: 카드명 한글화 - watchlistService가 이미 가진 CardNameKoResolver 폴백 로직을 재사용한다
        // (즉시 알림 경로인 WatchlistService.notifyIfTargetAlreadyReached()와 항상 같은 표시명을 쓰도록).
        notificationService.createPriceTargetNotification(watchlist, watchlistService.resolveCardDisplayName(card), reachedTargetPrice);
    }
}
