package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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
import static org.mockito.Mockito.mock;

// WatchlistService.addWatchlist()의 동시 등록 방어(유저 단위 잠금 + UNIQUE 위반 변환)를 실제 DB로 검증한다.
// 여기서 검증하는 중복/제한 체크는 targetReached(체결가 조회) 결과와 무관하고, mock인 PriceTradeStatsRepository는
// 기본값(빈 리스트)만 반환해 항상 targetReached=false로 끝나므로(CardRepository/NotificationService 호출 없음)
// WatchlistRepository만 진짜로 연결하고 나머지는 mock으로 채운 WatchlistService를 직접 생성해 사용한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WatchlistConcurrencyTest {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private WatchlistService newWatchlistService() {
        return new WatchlistService(watchlistRepository, mock(PriceService.class),
                mock(CardRepository.class), mock(PriceTradeStatsRepository.class), mock(CardNameKoResolver.class),
                mock(NotificationService.class));
    }

    @Test
    @DisplayName("같은 카드에 동시 등록을 시도하면 하나만 성공하고 나머지는 DUPLICATE_WATCHLIST로 처리된다")
    void addWatchlist_concurrentSameCard_onlyOneSucceeds() throws InterruptedException {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate requiresNew = newRequiresNewTemplate();
        Long userId = requiresNew.execute(status -> insertUser("concurrent-dup-" + runId + "@test.com"));
        Long cardId = requiresNew.execute(status -> insertCard("watch-concurrent-dup-" + runId));
        WatchlistService watchlistService = newWatchlistService();

        try {
            int threadCount = 5;
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> requiresNew.execute(status -> {
                    try {
                        watchlistService.addWatchlist(userId, new WatchlistCreateRequest(cardId, null, 1000, null));
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_WATCHLIST);
                        return false;
                    }
                }));
            }

            List<Boolean> results = runConcurrently(tasks);

            assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
            Long finalCount = requiresNew.execute(status -> watchlistRepository.countByUserId(userId));
            assertThat(finalCount).isEqualTo(1L);
        } finally {
            requiresNew.executeWithoutResult(status -> cleanup(userId, List.of(cardId)));
        }
    }

    @Test
    @DisplayName("19개 보유 상태에서 동시에 2건을 등록해도 20개를 넘지 않는다")
    void addWatchlist_concurrentNearLimit_neverExceedsLimit() throws InterruptedException {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate requiresNew = newRequiresNewTemplate();
        Long userId = requiresNew.execute(status -> insertUser("concurrent-limit-" + runId + "@test.com"));
        WatchlistService watchlistService = newWatchlistService();
        List<Long> cardIds = new ArrayList<>();

        try {
            requiresNew.executeWithoutResult(status -> {
                for (int i = 0; i < 19; i++) {
                    Long existingCardId = insertCard("watch-limit-existing-" + runId + "-" + i);
                    cardIds.add(existingCardId);
                    watchlistRepository.save(Watchlist.builder()
                            .userId(userId).cardId(existingCardId).targetBuyPrice(1000).build());
                }
            });

            Long newCardA = requiresNew.execute(status -> insertCard("watch-limit-new-a-" + runId));
            Long newCardB = requiresNew.execute(status -> insertCard("watch-limit-new-b-" + runId));
            cardIds.add(newCardA);
            cardIds.add(newCardB);

            List<Callable<Boolean>> tasks = List.of(
                    () -> requiresNew.execute(status -> {
                        try {
                            watchlistService.addWatchlist(userId, new WatchlistCreateRequest(newCardA, null, 1000, null));
                            return true;
                        } catch (BusinessException e) {
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
                            return false;
                        }
                    }),
                    () -> requiresNew.execute(status -> {
                        try {
                            watchlistService.addWatchlist(userId, new WatchlistCreateRequest(newCardB, null, 1000, null));
                            return true;
                        } catch (BusinessException e) {
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
                            return false;
                        }
                    })
            );

            List<Boolean> results = runConcurrently(tasks);

            assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
            Long finalCount = requiresNew.execute(status -> watchlistRepository.countByUserId(userId));
            assertThat(finalCount).isEqualTo(20L);
        } finally {
            requiresNew.executeWithoutResult(status -> cleanup(userId, cardIds));
        }
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(task));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("모든 스레드가 타임아웃 전에 종료되어야 한다").isTrue();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(getUnchecked(future));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Boolean getUnchecked(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanup(Long userId, List<Long> cardIds) {
        watchlistRepository.deleteAll(watchlistRepository.findByUserId(userId));
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
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Concurrency Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
