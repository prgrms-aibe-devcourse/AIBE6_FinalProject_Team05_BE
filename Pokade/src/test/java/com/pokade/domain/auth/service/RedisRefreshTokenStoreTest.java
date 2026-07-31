package com.pokade.domain.auth.service;

import com.pokade.global.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
@Import({RedisRefreshTokenStore.class, RedisRefreshTokenStoreTest.TestConfig.class})
class RedisRefreshTokenStoreTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-key-at-least-32-bytes-0123456789",
                    Duration.ofMinutes(30),
                    Duration.ofDays(14)
            );
        }
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Autowired
    RedisRefreshTokenStore store;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("save하면 원문이 아닌 해시가 저장되고 TTL이 설정된다")
    void save_storesHashedTokenWithTtl() {
        store.save(1L, "refresh-token");

        String raw = redisTemplate.opsForValue().get("auth:refresh:1");
        assertThat(raw).isNotNull().isNotEqualTo("refresh-token");
        Long ttl = redisTemplate.getExpire("auth:refresh:1");
        assertThat(ttl).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("exists는 save 후 true, 저장이 없으면 false")
    void exists_reflectsPresence() {
        assertThat(store.exists(1L)).isFalse();
        store.save(1L, "refresh-token");
        assertThat(store.exists(1L)).isTrue();
    }

    @Test
    @DisplayName("compareAndRotate는 현재 refresh와 일치하면 회전하고 옛 토큰을 grace로 옮긴다")
    void compareAndRotate_rotatesAndMovesOldToGrace() {
        store.save(1L, "R0");

        assertThat(store.compareAndRotate(1L, "R0", "R1")).isTrue();
        assertThat(store.matchesGrace(1L, "R0")).isTrue();           // 옛 current -> grace
        assertThat(store.compareAndRotate(1L, "R1", "R2")).isTrue(); // 새 current는 R1
    }

    @Test
    @DisplayName("compareAndRotate는 현재 refresh와 다르면 회전하지 않고 false")
    void compareAndRotate_falseWhenNotCurrent() {
        store.save(1L, "R0");

        assertThat(store.compareAndRotate(1L, "WRONG", "R1")).isFalse();
        assertThat(store.compareAndRotate(1L, "R0", "R1")).isTrue(); // current는 그대로 R0였음
    }

    @Test
    @DisplayName("compareAndRotate는 저장이 없으면 false")
    void compareAndRotate_falseWhenAbsent() {
        assertThat(store.compareAndRotate(999L, "R0", "R1")).isFalse();
    }

    @Test
    @DisplayName("같은 현재 refresh로 두 번째 회전은 실패하고 grace로 수렴한다 (동시요청 double-rotate 방지)")
    void compareAndRotate_secondCallWithSameCurrentHitsGrace() {
        store.save(1L, "R0");

        assertThat(store.compareAndRotate(1L, "R0", "R1a")).isTrue();  // A: 회전 성공
        assertThat(store.compareAndRotate(1L, "R0", "R1b")).isFalse(); // B: 같은 R0 -> 재회전 차단
        assertThat(store.matchesGrace(1L, "R0")).isTrue();            // B는 grace로 수렴
    }

    @Test
    @DisplayName("compareAndRotate 시 grace TTL이 5초 이하로 설정된다")
    void compareAndRotate_graceTtlIsShort() {
        store.save(1L, "R0");
        store.compareAndRotate(1L, "R0", "R1");

        Long ttl = redisTemplate.getExpire("auth:refresh:grace:1");
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("matchesGrace는 grace로 옮겨진 옛 토큰이면 true, 아니면 false")
    void matchesGrace_reflectsGraceContent() {
        store.save(1L, "R0");
        store.compareAndRotate(1L, "R0", "R1");

        assertThat(store.matchesGrace(1L, "R0")).isTrue();
        assertThat(store.matchesGrace(1L, "other")).isFalse();
    }

    @Test
    @DisplayName("delete하면 refresh와 grace가 모두 제거된다")
    void delete_removesBoth() {
        store.save(1L, "R0");
        store.compareAndRotate(1L, "R0", "R1"); // current=R1, grace=R0

        store.delete(1L);

        assertThat(store.exists(1L)).isFalse();
        assertThat(store.matchesGrace(1L, "R0")).isFalse();
    }
}
