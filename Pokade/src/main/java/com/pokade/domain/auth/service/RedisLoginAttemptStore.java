package com.pokade.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String KEY_PREFIX = "auth:login:attempt:";
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BLOCK_TTL =  Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void recordFailure(String email) {
        Long count = redisTemplate.opsForValue().increment(KEY_PREFIX + email);
        if (count != null && count == 1) {
            redisTemplate.expire(KEY_PREFIX + email, BLOCK_TTL);
        }
    }

    @Override
    public boolean isBlocked(String email) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        return value != null && Long.parseLong(value) >= MAX_ATTEMPTS;
    }

    @Override
    public void reset(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}
