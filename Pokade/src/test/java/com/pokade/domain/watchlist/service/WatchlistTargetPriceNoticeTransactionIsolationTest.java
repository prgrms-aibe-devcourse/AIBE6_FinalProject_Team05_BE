package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 스케줄러가 여러 워치리스트를 한 트랜잭션으로 처리하면, 한 건에서 던진 예외가 물리 트랜잭션을 통째로
// rollback-only로 표시해(참여 트랜잭션의 전형적인 함정) 바깥 try/catch로 잡아도 커밋 시점에
// UnexpectedRollbackException으로 "이전에 이미 성공 처리된 다른 건들"까지 롤백되는 문제가 있었다.
// WatchlistTargetPriceNoticeProcessor를 REQUIRES_NEW로 분리한 뒤 이 문제가 실제로 해결됐는지,
// 실제 Postgres + 실제 Spring 트랜잭션 프록시로 검증한다(mock 기반 단위 테스트로는 AOP가 개입하지 않아
// 이 버그 자체가 재현되지 않는다).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WatchlistTargetPriceNoticeService.class, WatchlistTargetPriceNoticeProcessor.class,
        WatchlistService.class, com.pokade.domain.notification.service.NotificationService.class,
        com.pokade.domain.notification.store.SseEmitterStore.class})
class WatchlistTargetPriceNoticeTransactionIsolationTest {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WatchlistTargetPriceNoticeService noticeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private CardRepository cardRepository;

    @MockitoBean
    private PriceTradeStatsRepository priceTradeStatsRepository;

    @MockitoBean
    private PriceService priceService;

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    @Test
    @DisplayName("한 워치리스트 처리 중 예외가 나도, 이전에 이미 성공 처리된 다른 워치리스트의 알림/isNotified는 롤백되지 않는다")
    void oneItemFails_previouslyCommittedItemSurvives() {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate requiresNew = newRequiresNewTemplate();

        Long userId = requiresNew.execute(status -> insertUser("tx-isolation-" + runId + "@test.com"));
        Long okCardId = requiresNew.execute(status -> insertCard("watch-tx-ok-" + runId));
        Long failCardId = requiresNew.execute(status -> insertCard("watch-tx-fail-" + runId));
        // Notification.message는 VARCHAR(255) - 카드명을 300자로 만들면 메시지 조립 시 컬럼 길이를 넘어
        // notificationRepository.save()에서 실제 DataIntegrityViolationException이 발생한다(원래 버그의
        // "참여 트랜잭션에서 예외가 던져지는" 조건을 인위적 훅 없이 실제 제약 위반으로 재현).
        String tooLongCardName = "가".repeat(300);

        Long okWatchlistId = requiresNew.execute(status -> watchlistRepository.save(
                Watchlist.builder().userId(userId).cardId(okCardId).targetBuyPrice(1000).build()).getId());
        Long failWatchlistId = requiresNew.execute(status -> watchlistRepository.save(
                Watchlist.builder().userId(userId).cardId(failCardId).targetBuyPrice(1000).build()).getId());

        given(cardRepository.findAllById(any())).willReturn(List.of(
                Card.builder().id(okCardId).name("리자몽").build(),
                Card.builder().id(failCardId).name(tooLongCardName).build()));

        PriceRange okRange = new PriceRange(okCardId, 900, 1100);
        PriceRange failRange = new PriceRange(failCardId, 900, 1100);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(any(), any(), any()))
                .willReturn(List.of(okRange, failRange));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    List<Long> ids = invocation.getArgument(0);
                    return ids.get(0).equals(okCardId) ? List.of(okRange) : List.of(failRange);
                });

        try {
            requiresNew.executeWithoutResult(status -> noticeService.detectTargetPriceReached());

            Watchlist okReloaded = requiresNew.execute(status -> watchlistRepository.findById(okWatchlistId).orElseThrow());
            assertThat(okReloaded.isNotified()).isTrue();

            List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getMessage()).contains("리자몽");

            Watchlist failReloaded = requiresNew.execute(status -> watchlistRepository.findById(failWatchlistId).orElseThrow());
            assertThat(failReloaded.isNotified()).isFalse();
        } finally {
            requiresNew.executeWithoutResult(status ->
                    cleanup(userId, List.of(okWatchlistId, failWatchlistId), List.of(okCardId, failCardId)));
        }
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void cleanup(Long userId, List<Long> watchlistIds, List<Long> cardIds) {
        notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
        watchlistIds.forEach(watchlistRepository::deleteById);
        entityManager.flush();
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cards WHERE id IN :cardIds")
                .setParameter("cardIds", cardIds)
                .executeUpdate();
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Tx Isolation Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
