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
}
