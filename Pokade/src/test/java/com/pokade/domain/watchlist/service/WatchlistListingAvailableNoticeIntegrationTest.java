package com.pokade.domain.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.card.support.PokedexKoNameCache;
import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.service.ListingService;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.user.service.UserAccessGuard;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

// listing 도메인이 실제로 ListingCreatedEvent를 발행하고, watchlist/notification 도메인이 그걸 구독해
// 재입고 알림을 만드는 전체 경로를 검증한다(#300). 단위 테스트(WatchlistListingAvailableNoticeListenerTest)는
// 리스너를 직접 호출해 판단 로직만 보므로, Spring의 @TransactionalEventListener(AFTER_COMMIT) 배선
// 자체(트랜잭션이 실제로 커밋된 뒤에만 동작하는지)는 이 통합 테스트가 아니면 검증할 방법이 없다.
//
// @SpringBootTest(전체 컨텍스트) 대신 @DataJpaTest + 필요한 빈만 @Import한 슬라이스를 쓴다 - 전체
// 컨텍스트는 S3/메일/OAuth2 등 이 테스트와 무관한 빈까지 띄우며 실제 자격 증명을 요구해 로컬에서 깨진다.
//
// 클래스에 @Transactional(NOT_SUPPORTED)을 직접 선언해 @DataJpaTest가 기본으로 씌우는 "테스트당 1개
// 트랜잭션(끝나면 롤백)"을 끈다 - 그 기본 동작 위에서는 아래 TransactionTemplate로 열었다 커밋한
// "것처럼 보이는" 트랜잭션도 실제로는 바깥의 테스트 트랜잭션에 참여할 뿐이라 물리적으로 커밋되지
// 않고, AFTER_COMMIT 리스너가 테스트 도중 전혀 발동하지 않는다.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ListingService.class, WatchlistListingAvailableNoticeListener.class, NotificationService.class,
        CardNameKoResolver.class, PokedexKoNameCache.class, UserAccessGuard.class, SseEmitterStore.class})
class WatchlistListingAvailableNoticeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    // 클래스 트랜잭션을 껐으므로(위 주석 참고) 준비 데이터 삽입도 각자 자기 트랜잭션에서 커밋해야
    // entityManager.persist()가 TransactionRequiredException 없이 동작한다.
    private Long persistActiveUser(String email) {
        // nickname은 UNIQUE 제약이라 email에서 파생시킨다 - 이 테스트는 클래스 트랜잭션을 껐으므로
        // (AFTER_COMMIT 검증을 위해 필요) 테스트 메서드 사이에 데이터가 자동 롤백되지 않는다.
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue());
    }

    private Long persistCard(String externalId, String name) {
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, :name) RETURNING id")
                .setParameter("externalId", externalId)
                .setParameter("name", name)
                .getSingleResult()).longValue());
    }

    private Long persistWatchlist(Long userId, Long cardId) {
        return persistWatchlist(userId, cardId, null);
    }

    private Long persistWatchlist(Long userId, Long cardId, Long variantId) {
        return transactionTemplate().execute(status -> {
            Watchlist watchlist = Watchlist.builder()
                    .userId(userId).cardId(cardId).variantId(variantId).targetBuyPrice(1000).build();
            entityManager.persist(watchlist);
            return watchlist.getId();
        });
    }

    private Long persistVariant(Long cardId, String variantName, boolean primary) {
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) "
                                + "VALUES (:cardId, :variantName, :primary, now()) RETURNING id")
                .setParameter("cardId", cardId)
                .setParameter("variantName", variantName)
                .setParameter("primary", primary)
                .getSingleResult()).longValue());
    }

    private void runInNewTransaction(Runnable action) {
        transactionTemplate().executeWithoutResult(status -> action.run());
    }

    @Test
    void 매물이_처음_등록되면_워치리스트_등록자에게_재입고_알림이_생성된다() {
        Long sellerId = persistActiveUser("listing-notice-seller1@test.com");
        Long watcherId = persistActiveUser("listing-notice-watcher1@test.com");
        Long cardId = persistCard("listing-notice-card-1", "Charizard");
        Long watchlistId = persistWatchlist(watcherId, cardId);

        runInNewTransaction(() ->
                listingService.createListing(sellerId, new ListingCreateRequest(cardId, null, 10000, ListingGrade.A)));

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId);
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.LISTING_AVAILABLE);
        Watchlist reloaded = watchlistRepository.findById(watchlistId).orElseThrow();
        assertThat(reloaded.isListingNotified()).isTrue();
    }

    @Test
    void 매물_등록_트랜잭션이_롤백되면_재입고_알림이_생성되지_않는다() {
        Long sellerId = persistActiveUser("listing-notice-seller2@test.com");
        Long watcherId = persistActiveUser("listing-notice-watcher2@test.com");
        Long cardId = persistCard("listing-notice-card-2", "Blastoise");
        persistWatchlist(watcherId, cardId);

        transactionTemplate().executeWithoutResult(status -> {
            listingService.createListing(sellerId, new ListingCreateRequest(cardId, null, 10000, ListingGrade.A));
            status.setRollbackOnly();
        });

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId);
        assertThat(notifications).isEmpty();
    }

    @Test
    void 이미_매물이_있는_카드에_매물이_추가로_등록되면_재입고_알림이_가지_않는다() {
        Long seller1Id = persistActiveUser("listing-notice-seller3a@test.com");
        Long seller2Id = persistActiveUser("listing-notice-seller3b@test.com");
        Long watcherId = persistActiveUser("listing-notice-watcher3@test.com");
        Long cardId = persistCard("listing-notice-card-3", "Pikachu");
        persistWatchlist(watcherId, cardId);

        runInNewTransaction(() ->
                listingService.createListing(seller1Id, new ListingCreateRequest(cardId, null, 10000, ListingGrade.A)));
        // 첫 등록으로 이미 재입고 알림 1건이 생성된 상태 - 두 번째 등록은 "이미 매물이 있던 카드"라 알림이 추가되면 안 된다.
        int notificationCountAfterFirst = notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId).size();

        runInNewTransaction(() ->
                listingService.createListing(seller2Id, new ListingCreateRequest(cardId, null, 12000, ListingGrade.A)));

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId);
        assertThat(notifications).hasSize(notificationCountAfterFirst);
    }

    @Test
    void 다른_variant를_관심등록한_워치리스트에는_재입고_알림이_가지_않는다() {
        Long sellerId = persistActiveUser("listing-notice-seller4@test.com");
        Long watcherId = persistActiveUser("listing-notice-watcher4@test.com");
        Long cardId = persistCard("listing-notice-card-4", "Charizard");
        Long variantA = persistVariant(cardId, "holo", true);
        Long variantB = persistVariant(cardId, "normal", false);
        Long watchlistId = persistWatchlist(watcherId, cardId, variantA);

        // variantB로 매물 등록 - watcherId는 variantA만 관심 등록했으니 알림이 가면 안 된다.
        runInNewTransaction(() ->
                listingService.createListing(sellerId, new ListingCreateRequest(cardId, variantB, 10000, ListingGrade.A)));

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId)).isEmpty();
        assertThat(watchlistRepository.findById(watchlistId).orElseThrow().isListingNotified()).isFalse();

        // variantA로 매물 등록 - 이번엔 관심 등록한 variant와 일치하므로 알림이 간다.
        runInNewTransaction(() ->
                listingService.createListing(sellerId, new ListingCreateRequest(cardId, variantA, 9000, ListingGrade.A)));

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(watcherId))
                .extracting(Notification::getType).contains(NotificationType.LISTING_AVAILABLE);
        assertThat(watchlistRepository.findById(watchlistId).orElseThrow().isListingNotified()).isTrue();
    }
}
