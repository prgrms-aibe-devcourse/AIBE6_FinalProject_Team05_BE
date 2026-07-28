package com.pokade.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY_PREFIX = "auth:verify:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:verify:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:verify:attempt:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isRecentlySent(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email));
    }

    @Override
    public void save(String email, String code) {
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + email, "1", COOLDOWN_TTL);
    }

    @Override
    public Optional<String> find(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email));
    }

    @Override
    public long getAttemptCount(String email) {
        String value = redisTemplate.opsForValue().get(ATTEMPT_KEY_PREFIX + email);
        return value != null ? Long.parseLong(value) : 0;
    }

    @Override
    public void incrementAttempt(String email) {
        Long count = redisTemplate.opsForValue().increment(ATTEMPT_KEY_PREFIX + email);
        if (count != null && count == 1) {
            redisTemplate.expire(ATTEMPT_KEY_PREFIX + email, CODE_TTL);
        }
    }

    @Override
    public void delete(String email) {
        redisTemplate.delete(CODE_KEY_PREFIX + email);
        redisTemplate.delete(COOLDOWN_KEY_PREFIX + email);
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
    }
}
