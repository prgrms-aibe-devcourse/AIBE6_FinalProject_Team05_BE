package com.pokade.domain.price.store;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
@Import(PriceRankingRefreshStore.class)
class PriceRankingRefreshStoreTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    PriceRankingRefreshStore store;
    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        redisTemplate.delete("priceRanking:refreshedAt:rise");
    }

    @Test
    @DisplayName("한 번도 기록된 적 없으면 null을 반환한다")
    void findRefreshedAt_returnsNullWhenNeverRecorded() {
        assertThat(store.findRefreshedAt("rise")).isNull();
    }

    @Test
    @DisplayName("recordNow로 기록한 시각을 그대로 조회할 수 있다")
    void recordNow_thenFindReturnsSameValue() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        store.recordNow("rise", now);

        assertThat(store.findRefreshedAt("rise")).isEqualTo(now);
    }

    @Test
    @DisplayName("recordNow는 TTL을 설정한다(48시간 이내)")
    void recordNow_setsTtl() {
        store.recordNow("rise", LocalDateTime.now());

        Long ttl = redisTemplate.getExpire("priceRanking:refreshedAt:rise");
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(48 * 3600L);
    }
}
