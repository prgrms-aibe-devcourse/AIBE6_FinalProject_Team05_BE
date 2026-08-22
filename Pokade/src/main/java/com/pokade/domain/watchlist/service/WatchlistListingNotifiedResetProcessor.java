package com.pokade.domain.watchlist.service;

import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// WatchlistTargetPriceNoticeProcessor와 같은 이유로 별도 빈으로 분리한다(self-invocation 시
// @Transactional이 AOP 프록시를 안 거쳐 적용되지 않는 문제) - 워치리스트 1건마다 독립된 새 트랜잭션에서
// 처리해 한 건의 예외가 다른 건에 영향을 주지 않는다(#300 후속).
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistListingNotifiedResetProcessor {

    private final WatchlistRepository watchlistRepository;
    private final ListingRepository listingRepository;

    // resolvedVariantId: 호출자(WatchlistListingNotifiedResetScheduler)가 watchlist.variantId==null이면
    // 카드의 대표 variant ID로 이미 치환해서 넘긴다(WatchlistListingAvailableNoticeListener와 동일한
    // null=대표 변형 해석 규칙) - 대표 variant 정보 자체가 없으면 null 그대로 넘어온다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long watchlistId, Long resolvedVariantId) {
        // 새 트랜잭션에서 재조회한다 - 스케줄러가 후보를 읽은 시점과 이 트랜잭션이 열리는 시점 사이에
        // 삭제/변경(예: 그 사이 재입고 알림이 다시 발송됨)됐을 수 있어 이전 인스턴스를 그대로 쓰지 않는다.
        Watchlist watchlist = watchlistRepository.findById(watchlistId).orElse(null);
        if (watchlist == null || !watchlist.isListingNotified()) {
            return;
        }

        long activeListingCount = listingRepository.countByCardIdAndVariantIdAndStatus(
                watchlist.getCardId(), resolvedVariantId, ListingStatus.ACTIVE);
        if (activeListingCount > 0) {
            // 아직 활성 매물이 남아있음 - 리셋하지 않는다.
            return;
        }

        // 조건부 원자적 UPDATE로 "리셋 권한"을 먼저 선점한다 - 그 사이 재입고 알림 리스너가 먼저
        // markListingNotifiedIfNotYet으로 다시 true를 선점했다면(막 재입고된 경우) 0이 반환돼 안전하게 스킵한다.
        int reset = watchlistRepository.resetListingNotifiedIfTrue(watchlist.getId());
        if (reset == 0) {
            log.info("워치리스트 재입고 알림 리셋 권한을 확보하지 못해 스킵합니다: watchlistId={}", watchlistId);
            return;
        }
        watchlist.resetListingNotified();
    }
}
