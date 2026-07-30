package com.pokade.domain.auth.service;

import com.pokade.global.security.JwtProperties;
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

    @Autowired
    RedisRefreshTokenStore store;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("save하면 auth:refresh:<userId> 키에 토큰이 저장되고 TTL이 설정된다")
    void save_storesTokenWithTtl() {
        store.save(1L, "refresh-token");

        assertThat(redisTemplate.opsForValue().get("auth:refresh:1")).isEqualTo("refresh-token");
        Long ttl = redisTemplate.getExpire("auth:refresh:1");
        assertThat(ttl).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("find는 저장된 refresh 토큰을 반환한다")
    void find_returnsStoredToken() {
        store.save(1L, "refresh-token");

        assertThat(store.find(1L)).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("find는 저장된 값이 없으면 null을 반환한다")
    void find_returnsNullWhenAbsent() {
        assertThat(store.find(999L)).isNull();
    }

    @Test
    @DisplayName("saveGrace하면 auth:refresh:grace:<userId>에 저장되고 TTL이 60초 이하로 설정된다")
    void saveGrace_storesGraceTokenWithShortTtl() {
        store.saveGrace(1L, "old-refresh");

        assertThat(redisTemplate.opsForValue().get("auth:refresh:grace:1")).isEqualTo("old-refresh");
        Long ttl = redisTemplate.getExpire("auth:refresh:grace:1");
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("findGrace는 저장된 grace 토큰을 반환한다")
    void findGrace_returnsStoredGraceToken() {
        store.saveGrace(1L, "old-refresh");

        assertThat(store.findGrace(1L)).isEqualTo("old-refresh");
    }

    @Test
    @DisplayName("delete하면 refresh와 grace 토큰이 함께 제거된다")
    void delete_removesBothRefreshAndGrace() {
        store.save(1L, "refresh-token");
        store.saveGrace(1L, "old-refresh");

        store.delete(1L);

        assertThat(store.find(1L)).isNull();
        assertThat(store.findGrace(1L)).isNull();
    }
}
