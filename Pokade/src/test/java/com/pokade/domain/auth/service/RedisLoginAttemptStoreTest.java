package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.RedisLoginAttemptStore;
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
@Import(RedisLoginAttemptStore.class)
class RedisLoginAttemptStoreTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @BeforeEach
    void clean() {
        redisTemplate.delete("auth:login:attempt:user@pokade.com");
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    RedisLoginAttemptStore store;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("실패 횟수가 임계값(10) 미만이면 차단되지 않는다")
    void notBlockedUnderThreshold() {
        for (int i = 0; i < 9; i++) store.recordFailure("user@pokade.com");

        assertThat(store.isBlocked("user@pokade.com")).isFalse();
    }

    @Test
    @DisplayName("실패 횟수가 임계값(10)에 도달하면 차단된다")
    void blockedAtThreshold() {
        for (int i = 0; i < 10; i++) store.recordFailure("user@pokade.com");

        assertThat(store.isBlocked("user@pokade.com")).isTrue();
    }

    @Test
    @DisplayName("recordFailure는 카운터에 TTL을 설정한다(자동 해제)")
    void recordFailure_setsTtl() {
        store.recordFailure("user@pokade.com");

        Long ttl = redisTemplate.getExpire("auth:login:attempt:user@pokade.com");
        assertThat(ttl).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("reset하면 카운터가 초기화되어 차단이 풀린다")
    void reset_clearsCounter() {
        for (int i = 0; i < 10; i++) store.recordFailure("user@pokade.com");

        store.reset("user@pokade.com");

        assertThat(store.isBlocked("user@pokade.com")).isFalse();
    }
}