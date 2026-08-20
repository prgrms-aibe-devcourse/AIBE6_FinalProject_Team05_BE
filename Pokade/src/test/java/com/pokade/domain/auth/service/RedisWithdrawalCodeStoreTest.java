package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.RedisWithdrawalCodeStore;
import com.pokade.domain.auth.store.VerificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
@Import(RedisWithdrawalCodeStore.class)
public class RedisWithdrawalCodeStoreTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    RedisWithdrawalCodeStore store;

    // ===== 기본 동작 =====
    @Test
    @DisplayName("verifyAndConsume: 일치하면 OK, 재조회는 EXPIRED(코드 소모됨)")
    void verify_okThenConsumed() {
        String email = "va@pokade.com";
        store.save(email, "123456");

        assertThat(store.verifyAndConsume(email, "123456")).isEqualTo(VerificationResult.OK);
        assertThat(store.verifyAndConsume(email, "123456")).isEqualTo(VerificationResult.EXPIRED);
    }

    @Test
    @DisplayName("verifyAndConsume: 불일치는 MISMATCH, 5회 채우면 이후는 EXCEEDED(정답도 차단)")
    void verify_mismatchThenExceeded() {
        String email = "va-limit@pokade.com";
        store.save(email, "123456");

        for (int i = 0; i < 5; i++) {
            assertThat(store.verifyAndConsume(email, "000000")).isEqualTo(VerificationResult.MISMATCH);
        }
        assertThat(store.verifyAndConsume(email, "000000")).isEqualTo(VerificationResult.EXCEEDED);
        assertThat(store.verifyAndConsume(email, "123456")).isEqualTo(VerificationResult.EXCEEDED); // 초과 상태에선 정답도 차단
    }

    @Test
    @DisplayName("verifyAndConsume: 저장 전이면 EXPIRED")
    void verify_noCode_expired() {
        assertThat(store.verifyAndConsume("none@pokade.com", "123456")).isEqualTo(VerificationResult.EXPIRED);
    }

    // ===== 동시성(원자성 실증) =====
    @Test
    @DisplayName("동시성: 정답 코드로 100건 동시 요청 → 정확히 1건만 OK, 나머지 EXPIRED(단일 소모 보장)")
    void concurrent_singleConsume() throws Exception {
        String email = "concurrent-consume@pokade.com";
        store.save(email, "123456");
        int n = 100;

        List<VerificationResult> results = runConcurrently(n, () -> store.verifyAndConsume(email, "123456"));

        long ok = results.stream().filter(r -> r == VerificationResult.OK).count();
        long expired = results.stream().filter(r -> r == VerificationResult.EXPIRED).count();
        assertThat(ok).isEqualTo(1);
        assertThat(expired).isEqualTo(n - 1);
    }

    @Test
    @DisplayName("동시성: 오답 코드로 100건 동시 요청 → MISMATCH 정확히 5, 나머지 EXCEEDED(시도제한 우회 불가)")
    void concurrent_attemptLimit() throws Exception {
        String email = "concurrent-limit@pokade.com";
        store.save(email, "123456");
        int n = 100;

        List<VerificationResult> results = runConcurrently(n, () -> store.verifyAndConsume(email, "000000"));

        long mismatch = results.stream().filter(r -> r == VerificationResult.MISMATCH).count();
        long exceeded = results.stream().filter(r -> r == VerificationResult.EXCEEDED).count();
        assertThat(mismatch).isEqualTo(5);
        assertThat(exceeded).isEqualTo(n - 5);
    }

    // n개 작업을 배리어로 최대한 동시에 출발시켜 실행한다
    private List<VerificationResult> runConcurrently(int n, Callable<VerificationResult> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        List<Future<VerificationResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                barrier.await();
                return task.call();
            }));
        }
        List<VerificationResult> results = new ArrayList<>();
        for (Future<VerificationResult> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}
