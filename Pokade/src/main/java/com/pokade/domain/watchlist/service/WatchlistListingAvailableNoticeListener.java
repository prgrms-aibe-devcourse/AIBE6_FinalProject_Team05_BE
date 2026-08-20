package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.event.ListingCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

// 관심 있는 카드에 매물이 없다가 새로 생겼을 때(재입고) 워치리스트 등록자에게 알림을 보낸다(#300).
// 매물 등록 트랜잭션이 실제로 커밋된 뒤에만 동작해야(그새 롤백되는 경우 유령 알림이 생기지 않도록)
// AFTER_COMMIT에서 구독하고, 리스너 자체는 독립된 새 트랜잭션에서 DB에 쓴다.
//
// 스코프 제한(이번 범위에서 의도적으로 제외):
// - variant 단위 판단 없음 - ListingRepository.countByCardIdAndStatus가 카드 단위라 이와 동일하게 카드 단위로만 판단한다.
// - 재알림 리셋 없음 - listingNotified는 한 번 true가 되면 매물이 다시 소진돼도 리셋되지 않는다.
// - 같은 카드로 두 매물이 거의 동시에 등록되는 TOCTOU 경합(둘 다 count==1로 볼 수 있음)은 감내한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistListingAvailableNoticeListener {

    private final WatchlistRepository watchlistRepository;
    private final ListingRepository listingRepository;
    private final CardRepository cardRepository;
    private final CardNameKoResolver cardNameKoResolver;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingCreated(ListingCreatedEvent event) {
        long activeListingCount = listingRepository.countByCardIdAndStatus(event.cardId(), ListingStatus.ACTIVE);
        if (activeListingCount != 1) {
            // 이미 매물이 있던 카드에 하나 더 등록된 경우(재입고 아님) - 알림 대상 없음.
            return;
        }

        List<Watchlist> watchers = watchlistRepository.findByCardIdAndListingNotifiedFalse(event.cardId());
        if (watchers.isEmpty()) {
            return;
        }

        Card card = cardRepository.findById(event.cardId()).orElse(null);
        if (card == null) {
            log.warn("재입고 알림 대상 카드를 찾을 수 없어 스킵합니다: cardId={}", event.cardId());
            return;
        }
        String cardName = Objects.requireNonNullElse(cardNameKoResolver.resolve(card), card.getName());

        for (Watchlist watchlist : watchers) {
            int claimed = watchlistRepository.markListingNotifiedIfNotYet(watchlist.getId());
            if (claimed == 0) {
                continue;
            }
            watchlist.markAsListingNotified();
            notificationService.createListingAvailableNotification(watchlist, cardName, card);
        }
    }
}
