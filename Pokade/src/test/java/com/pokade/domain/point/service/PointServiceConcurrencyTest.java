package com.pokade.domain.point.service;

import com.pokade.domain.point.repository.PointTransactionRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// UserRepository.findByIdWithLock()의 비관적 락이 동시 차감 요청에서 실제로 갱신유실/초과차감을 막는지
// 실제 DB(Testcontainers)로 검증한다. PointService는 findByIdWithLock을 거치므로 각 스레드는 서로를
// 기다렸다가 순차적으로 처리되어야 하고, 그 결과 잔액은 항상 정확히 맞아떨어져야 한다.
@DataJpaTest
class PointServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private PointService newPointService() {
        return new PointService(userRepository, pointTransactionRepository);
    }

    @Test
    @DisplayName("잔액과 정확히 맞아떨어지는 동시 차감 10건은 모두 성공하고, 잔액은 정확히 0이 된다")
    void use_concurrentExactBalance_allSucceedAndBalanceIsZero() throws InterruptedException {
        TransactionTemplate requiresNew = newRequiresNewTemplate();
        Long userId = requiresNew.execute(status -> insertUser("point-concurrency-exact@test.com", 10000));
        PointService pointService = newPointService();

        try {
            int threadCount = 10;
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> requiresNew.execute(status -> {
                    pointService.use(userId, 1000, null);
                    return true;
                }));
            }

            List<Boolean> results = runConcurrently(tasks);

            assertThat(results).filteredOn(Boolean::booleanValue).hasSize(10);
            User finalUser = requiresNew.execute(status -> userRepository.findById(userId).orElseThrow());
            assertThat(finalUser.getPointBalance()).isZero();
            long historyCount = requiresNew.execute(status ->
                    pointTransactionRepository.findAll().stream().filter(t -> t.getUserId().equals(userId)).count());
            assertThat(historyCount).isEqualTo(10L);
        } finally {
            requiresNew.executeWithoutResult(status -> cleanup(userId));
        }
    }

    @Test
    @DisplayName("잔액보다 많은 동시 차감 요청 중 잔액만큼만 성공하고, 잔액은 음수가 되지 않는다")
    void use_concurrentExceedsBalance_onlyAffordableAmountSucceeds() throws InterruptedException {
        TransactionTemplate requiresNew = newRequiresNewTemplate();
        Long userId = requiresNew.execute(status -> insertUser("point-concurrency-overdraft@test.com", 5000));
        PointService pointService = newPointService();

        try {
            int threadCount = 10; // 각 1000원씩 - 잔액(5000)으로는 5건만 감당 가능
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> requiresNew.execute(status -> {
                    try {
                        pointService.use(userId, 1000, null);
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT_BALANCE);
                        return false;
                    }
                }));
            }

            List<Boolean> results = runConcurrently(tasks);

            assertThat(results).filteredOn(Boolean::booleanValue).hasSize(5);
            User finalUser = requiresNew.execute(status -> userRepository.findById(userId).orElseThrow());
            assertThat(finalUser.getPointBalance()).isZero();
        } finally {
            requiresNew.executeWithoutResult(status -> cleanup(userId));
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

    private void cleanup(Long userId) {
        entityManager.createNativeQuery("DELETE FROM point_transactions WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private Long insertUser(String email, int pointBalance) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, point_balance) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE', :pointBalance) RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .setParameter("pointBalance", pointBalance)
                .getSingleResult()).longValue();
    }
}
