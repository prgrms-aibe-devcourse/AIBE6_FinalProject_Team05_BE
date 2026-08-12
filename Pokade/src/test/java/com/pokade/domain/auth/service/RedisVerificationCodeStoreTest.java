package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.RedisVerificationCodeStore;
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

import static org.assertj.core.api.Assertions.assertThat;


@DataRedisTest
@Testcontainers
@Import(RedisVerificationCodeStore.class)
public class RedisVerificationCodeStoreTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    RedisVerificationCodeStore store;

    @Test
    @DisplayName("save는 최초 호출 시 쿨다운을 선점해 true, 쿨다운 중 재호출은 false를 반환한다")
    void save_acquiresCooldownOnce() {
        String email = "user@pokade.com";
        assertThat(store.save(email, "123456")).isTrue();

        assertThat(store.save(email, "654321")).isFalse();
    }

    @Test
    @DisplayName("verifyAndConsume: 일치하면 OK, 재조회는 EXPIRED(코드 소모됨)")
    void verify_okThenConsumed() {
        String email = "ok@pokade.com";
        store.save(email, "123456");

        assertThat(store.verifyAndConsume(email, "123456")).isEqualTo(VerificationResult.OK);
        assertThat(store.verifyAndConsume(email, "123456")).isEqualTo(VerificationResult.EXPIRED);
    }

    @Test
    @DisplayName("verifyAndConsume: 저장 전이면 EXPIRED")
    void verify_noCode_expired() {
        assertThat(store.verifyAndConsume("none@pokade.com", "123456")).isEqualTo(VerificationResult.EXPIRED);
    }

    @Test
    @DisplayName("verifyAndConsume: 불일치는 MISMATCH, 5회 채우면 이후는 EXCEEDED")
    void verify_mismatchThenExceeded() {
        String email = "limit@pokade.com";
        store.save(email, "123456");

        for (int i = 0; i < 5; i++) {
            assertThat(store.verifyAndConsume(email, "000000")).isEqualTo(VerificationResult.MISMATCH);
        }
        assertThat(store.verifyAndConsume(email, "000000")).isEqualTo(VerificationResult.EXCEEDED);
    }
}
