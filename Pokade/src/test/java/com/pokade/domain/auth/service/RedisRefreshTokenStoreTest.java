package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.RedisRefreshTokenStore;
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

    // ===== 세션 스코프 (다중 세션) =====

    @Test
    @DisplayName("두 세션은 독립 공존하고, 한쪽 회전이 다른 세션을 건드리지 않는다")
    void twoSessions_independent_rotateOneKeepsOther() {
        store.save(5L, "A", "tokenA");
        store.save(5L, "B", "tokenB");

        assertThat(store.compareAndRotate(5L, "A", "tokenA", "tokenA2")).isTrue();
        assertThat(store.exists(5L, "B")).isTrue();                                 // B 생존
        assertThat(store.compareAndRotate(5L, "B", "tokenB", "tokenB2")).isTrue();  // B 정상
    }

    @Test
    @DisplayName("delete(userId, sid)는 그 세션만 삭제하고 다른 세션은 유지한다")
    void deleteSession_removesOnlyThatSession() {
        store.save(5L, "A", "tA");
        store.save(5L, "B", "tB");

        store.delete(5L, "A");

        assertThat(store.exists(5L, "A")).isFalse();
        assertThat(store.exists(5L, "B")).isTrue();
    }

    @Test
    @DisplayName("deleteAll(userId)는 그 유저의 모든 세션을 삭제하고 다른 유저는 건드리지 않는다")
    void deleteAll_removesAllSessionsOfUserOnly() {
        store.save(5L, "A", "tA");
        store.save(5L, "B", "tB");
        store.save(6L, "C", "tC");

        store.deleteAll(5L);

        assertThat(store.exists(5L, "A")).isFalse();
        assertThat(store.exists(5L, "B")).isFalse();
        assertThat(store.exists(6L, "C")).isTrue();
    }

    @Test
    @DisplayName("matchesGrace는 회전 후 그 세션의 grace에만 옛 토큰이 담긴다")
    void matchesGrace_scopedToSession() {
        store.save(5L, "A", "tA");
        store.compareAndRotate(5L, "A", "tA", "tA2");

        assertThat(store.matchesGrace(5L, "A", "tA")).isTrue();   // 그 세션 grace
        assertThat(store.matchesGrace(5L, "B", "tA")).isFalse();  // 다른 세션엔 없음
    }

    @Test
    @DisplayName("세션 키에 TTL이 설정된다")
    void sessionKey_hasTtl() {
        store.save(5L, "A", "tA");

        Long ttl = redisTemplate.getExpire("auth:refresh:5:A");
        assertThat(ttl).isNotNull().isGreaterThan(0);
    }
}
