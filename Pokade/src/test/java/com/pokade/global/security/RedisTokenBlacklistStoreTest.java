package com.pokade.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistStoreTest {

    private static final String KEY = "auth:blacklist:1";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);

    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    ValueOperations<String, String> valueOperations;

    RedisTokenBlacklistStore store;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("secret", ACCESS_TTL, Duration.ofDays(14));
        store = new RedisTokenBlacklistStore(redisTemplate, jwtProperties);
    }

    @Test
    @DisplayName("블랙리스트에 있으면 true")
    void contains_true() {
        given(redisTemplate.hasKey(KEY)).willReturn(true);

        assertThat(store.contains(1L)).isTrue();
    }

    @Test
    @DisplayName("블랙리스트에 없으면 false")
    void contains_false() {
        given(redisTemplate.hasKey(KEY)).willReturn(false);

        assertThat(store.contains(1L)).isFalse();
    }

    @Test
    @DisplayName("Redis 조회 실패 시 fail-open — 예외를 던지지 않고 false 반환")
    void contains_failOpen_onRedisError() {
        given(redisTemplate.hasKey(KEY)).willThrow(new DataAccessResourceFailureException("Redis down"));

        assertThatCode(() -> assertThat(store.contains(1L)).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등록 시 access 만료(30분) TTL로 저장")
    void blacklist_setsKeyWithAccessTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        store.blacklist(1L);

        then(valueOperations).should().set(KEY, "1", ACCESS_TTL);
    }
}
