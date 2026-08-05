package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.RedisVerificationCodeStore;
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
    @DisplayName("save한 코드는 find로 조회되고, 저장 전엔 빈 Optional이다")
    void find_returnsSavedCode() {
        String email = "find@pokade.com";
        assertThat(store.find(email)).isEmpty();

        store.save(email, "123456");

        assertThat(store.find(email)).contains("123456");
    }

    @Test
    @DisplayName("delete하면 저장된 코드가 제거되어 find가 빈 Optional을 반환한다")
    void delete_removesSavedCode() {
        String email = "delete@pokade.com";
        store.save(email, "123456");

        store.delete(email);

        assertThat(store.find(email)).isEmpty();
    }

    @Test
    @DisplayName("incrementAttempt를 호출한 만큼 getAttemptCount가 증가하고, 호출 전엔 0이다")
    void incrementAttempt_increasesCount() {
        String email = "attempt@pokade.com";
        assertThat(store.getAttemptCount(email)).isZero();

        store.incrementAttempt(email);
        store.incrementAttempt(email);

        assertThat(store.getAttemptCount(email)).isEqualTo(2L);
    }

    @Test
    @DisplayName("save(재발송)하면 실패 카운터가 리셋되어 getAttemptCount가 0이 된다")
    void save_resetsAttemptCount() {
        String email = "resend@pokade.com";
        store.incrementAttempt(email);
        store.incrementAttempt(email);
        assertThat(store.getAttemptCount(email)).isEqualTo(2L);

        store.save(email, "123456");

        assertThat(store.getAttemptCount(email)).isZero();
    }

    @Test
    @DisplayName("delete하면 실패 카운터도 제거되어 getAttemptCount가 0이 된다")
    void delete_resetsAttemptCount() {
        String email = "attempt-del@pokade.com";
        store.incrementAttempt(email);

        store.delete(email);

        assertThat(store.getAttemptCount(email)).isZero();
    }
}
