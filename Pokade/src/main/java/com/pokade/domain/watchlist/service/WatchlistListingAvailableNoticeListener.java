package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
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

// 관심 있는 카드(variant 포함)에 매물이 없다가 새로 생겼을 때(재입고) 워치리스트 등록자에게 알림을
// 보낸다(#300). 매물 등록 트랜잭션이 실제로 커밋된 뒤에만 동작해야(그새 롤백되는 경우 유령 알림이 생기지
// 않도록) AFTER_COMMIT에서 구독하고, 리스너 자체는 독립된 새 트랜잭션에서 DB에 쓴다.
//
// variantId는 null이면 "대표 변형 관심"을 뜻한다(WatchlistVariantResolver 참고). 워치리스트 매칭
// 시에는 양쪽 null을 실제 대표 variant ID로 치환해서 비교한다 - 그대로 null==null로만 비교하면
// "대표 변형에 관심 있다"고 등록한 워치리스트가 구체적 variantId로 올라온 재입고 매물을 놓친다.
//
// listingNotified 리셋(매물이 다시 소진되면 다음 재입고 때 또 알릴 수 있게 false로 되돌리는 것)은 이
// 리스너가 아니라 별도 배치(WatchlistListingNotifiedResetScheduler, #300 후속)가 담당한다 - 매물이 ACTIVE를
// 벗어나는 지점이 listing/trade/admin 3개 도메인에 흩어져 있어 이벤트 기반보다 배치가 결합이 적다.
//
// 스코프 제한(이번 범위에서 의도적으로 제외):
// - 같은 카드로 두 매물이 거의 동시에 등록되는 TOCTOU 경합(둘 다 count==1로 볼 수 있음)은 감내한다.
// - "유일한 활성 매물" count는 variant_id 리터럴 값 기준이라, null-variant 매물과 명시적 대표-variant
//   매물이 동시에 존재하는 드문 경우 실제보다 낮게 잡힐 수 있다(불필요한 알림이 한 번 더 갈 수 있는
//   정도의 사소한 영향이라 감내한다) - 워치리스트 매칭 쪽의 null 치환과 별개 이슈다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistListingAvailableNoticeListener {

    private final WatchlistRepository watchlistRepository;
    private final ListingRepository listingRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final CardNameKoResolver cardNameKoResolver;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingCreated(ListingCreatedEvent event) {
        long activeListingCount = listingRepository.countByCardIdAndVariantIdAndStatus(
                event.cardId(), event.variantId(), ListingStatus.ACTIVE);
        if (activeListingCount != 1) {
            // 같은 (카드, variant)에 이미 매물이 있던 상태에서 하나 더 등록된 경우(재입고 아님) - 알림 대상 없음.
            return;
        }

        List<Watchlist> candidates = watchlistRepository.findByCardIdAndListingNotifiedFalse(event.cardId());
        if (candidates.isEmpty()) {
            return;
        }

        // 대표 variant 정보 자체가 없으면(동기화 누락 등) primaryVariantId도 null로 남는다 - 그러면
        // event/워치리스트 양쪽 다 null로 남아 Objects.equals(null, null)로 안전하게 매칭된다.
        Long primaryVariantId = cardVariantRepository.findPrimaryVariantId(event.cardId()).orElse(null);
        Long resolvedListingVariantId = WatchlistVariantResolver.resolveOrPrimary(event.variantId(), primaryVariantId);
        List<Watchlist> watchers = candidates.stream()
                .filter(watchlist -> Objects.equals(
                        WatchlistVariantResolver.resolveOrPrimary(watchlist.getVariantId(), primaryVariantId),
                        resolvedListingVariantId))
                .toList();
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
