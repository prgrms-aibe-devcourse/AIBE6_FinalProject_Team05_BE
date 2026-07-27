package com.pokade.domain.auth.service;

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
    @DisplayName("save하면 isRecentlySent가 true, 저장 전엔 false")
    void isRecentlySent_reflectsSave() {
        String email = "user@pokade.com";
        assertThat(store.isRecentlySent(email)).isFalse();

        store.save(email, "123456");

        assertThat(store.isRecentlySent(email)).isTrue();
    }
}
