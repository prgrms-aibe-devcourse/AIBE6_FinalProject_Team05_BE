package com.pokade.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetCodeStore {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY_PREFIX = "auth:reset:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:reset:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:reset:attempt:";
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 or redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "return c", Long.class);
    private final StringRedisTemplate redisTemplate;

    public boolean isRecentlySent(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email));
    }

    public void save(String email, String code) {
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + email, "1", COOLDOWN_TTL);
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
    }

    public Optional<String> find(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email));
    }

    public long getAttemptCount(String email) {
        String value = redisTemplate.opsForValue().get(ATTEMPT_KEY_PREFIX + email);
        return value != null ? Long.parseLong(value) : 0;
    }

    public void incrementAttempt(String email) {
        redisTemplate.execute(INCR_WITH_TTL,
                List.of(ATTEMPT_KEY_PREFIX + email),
                String.valueOf(CODE_TTL.getSeconds()));
    }

    public void delete(String email) {
        redisTemplate.delete(CODE_KEY_PREFIX + email);
        redisTemplate.delete(COOLDOWN_KEY_PREFIX + email);
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
    }
}
