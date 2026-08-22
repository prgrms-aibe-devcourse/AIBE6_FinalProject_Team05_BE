package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 여러 배치 인스턴스가 동시에 같은 워치리스트를 후보로 읽어 알림을 생성하려는 상황(다중 인스턴스 배포,
// 스케줄러 실행 중첩 등)을 실제 스레드로 재현해, markAsNotifiedIfNotYet()의 조건부 원자적 UPDATE가
// 중복 알림 생성을 막는지 검증한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WatchlistTargetPriceNoticeConcurrencyTest {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    @Test
    @DisplayName("여러 인스턴스가 동시에 같은 워치리스트를 감지해도 알림은 정확히 1건만 생성된다")
    void detectTargetPriceReached_concurrentInstances_onlyOneNotificationCreated() throws InterruptedException {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate requiresNew = newRequiresNewTemplate();
        Long userId = requiresNew.execute(status -> insertUser("notice-race-" + runId + "@test.com"));
        Long cardId = requiresNew.execute(status -> insertCard("watch-notice-race-" + runId));
        Long watchlistId = requiresNew.execute(status -> watchlistRepository.save(
                Watchlist.builder().userId(userId).cardId(cardId).targetBuyPrice(1000).build()).getId());

        try {
            int instanceCount = 5;
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < instanceCount; i++) {
                tasks.add(() -> requiresNew.execute(status -> {
                    newScheduler(cardId).detectTargetPriceReached();
                    return null;
                }));
            }

            runConcurrently(tasks);

            List<Notification> notifications = notificationRepository.findAllByUserIdForTestVerification(userId);
            assertThat(notifications).hasSize(1);
            Watchlist reloaded = requiresNew.execute(status -> watchlistRepository.findById(watchlistId).orElseThrow());
            assertThat(reloaded.isNotified()).isTrue();
        } finally {
            requiresNew.executeWithoutResult(status -> cleanup(userId, watchlistId, cardId));
        }
    }

    // card/price 조회는 항상 "목표가 도달"로 고정 응답하는 mock으로 대체해, 오직 markAsNotifiedIfNotYet()의
    // 원자성만으로 중복 알림 방지가 되는지를 순수하게 검증한다.
    private WatchlistTargetPriceNoticeScheduler newScheduler(Long cardId) {
        CardRepository cardRepository = mock(CardRepository.class);
        given(cardRepository.findAllById(any())).willReturn(List.of(Card.builder().id(cardId).name("리자몽").build()));

        PriceTradeStatsRepository priceTradeStatsRepository = mock(PriceTradeStatsRepository.class);
        PriceRange range = new PriceRange(cardId, 900, 1100);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(any(), any(), any())).willReturn(List.of(range));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSince(any(), any(), any(), any())).willReturn(List.of(range));

        NotificationService notificationService = new NotificationService(notificationRepository, cardRepository, new SseEmitterStore(), event -> { });
        WatchlistTargetPriceEvaluator watchlistTargetPriceEvaluator = new WatchlistTargetPriceEvaluator(
                watchlistRepository, cardRepository, notificationService, mock(CardNameKoResolver.class));
        WatchlistTargetPriceNoticeProcessor processor = new WatchlistTargetPriceNoticeProcessor(
                watchlistRepository, priceTradeStatsRepository, notificationService, watchlistTargetPriceEvaluator);

        return new WatchlistTargetPriceNoticeScheduler(watchlistRepository, cardRepository,
                priceTradeStatsRepository, processor);
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("모든 스레드가 타임아웃 전에 종료되어야 한다").isTrue();
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void cleanup(Long userId, Long watchlistId, Long cardId) {
        notificationRepository.deleteAll(notificationRepository.findAllByUserIdForTestVerification(userId));
        watchlistRepository.deleteById(watchlistId);
        entityManager.flush();
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cards WHERE id = :cardId")
                .setParameter("cardId", cardId)
                .executeUpdate();
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Concurrency Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
