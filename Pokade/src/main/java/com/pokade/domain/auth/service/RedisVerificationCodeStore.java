package com.pokade.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY_PREFIX = "auth:verify:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:verify:cooldown:";

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
}
