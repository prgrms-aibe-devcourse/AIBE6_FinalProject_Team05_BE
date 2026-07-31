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
        assertThat(raw).isNotNull().isNotEqualTo("refresh-token"); // 원문이 아니라 해시
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
    @DisplayName("matches는 동일 원문 토큰이면 true를 반환한다")
    void matches_trueForSameToken() {
        store.save(1L, "refresh-token");

        assertThat(store.matches(1L, "refresh-token")).isTrue();
    }

    @Test
    @DisplayName("matches는 다른 토큰이면 false를 반환한다")
    void matches_falseForDifferentToken() {
        store.save(1L, "refresh-token");

        assertThat(store.matches(1L, "other-token")).isFalse();
    }

    @Test
    @DisplayName("matches는 저장된 값이 없으면 false를 반환한다")
    void matches_falseWhenAbsent() {
        assertThat(store.matches(999L, "refresh-token")).isFalse();
    }

    @Test
    @DisplayName("saveGrace하면 해시가 저장되고 TTL이 5초 이하로 설정된다")
    void saveGrace_storesHashedWithShortTtl() {
        store.saveGrace(1L, "old-refresh");

        String raw = redisTemplate.opsForValue().get("auth:refresh:grace:1");
        assertThat(raw).isNotNull().isNotEqualTo("old-refresh");
        Long ttl = redisTemplate.getExpire("auth:refresh:grace:1");
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("matchesGrace는 동일 원문 토큰이면 true, 다르면 false")
    void matchesGrace_matchesOnlySameToken() {
        store.saveGrace(1L, "old-refresh");

        assertThat(store.matchesGrace(1L, "old-refresh")).isTrue();
        assertThat(store.matchesGrace(1L, "other")).isFalse();
    }

    @Test
    @DisplayName("delete하면 refresh와 grace가 모두 제거된다")
    void delete_removesBoth() {
        store.save(1L, "refresh-token");
        store.saveGrace(1L, "old-refresh");

        store.delete(1L);

        assertThat(store.exists(1L)).isFalse();
        assertThat(store.matches(1L, "refresh-token")).isFalse();
        assertThat(store.matchesGrace(1L, "old-refresh")).isFalse();
    }
}
