package com.pokade.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String KEY_PREFIX = "auth:login:attempt:";
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BLOCK_TTL =  Duration.ofMinutes(15);
    // 로그인 실패 카운트 +1, 첫 실패에만 TTL — 원자 실행(2단계면 크래시 시 TTL 없는 키가 남아 영구 잠금)
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "return c", Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void recordFailure(String email) {
        Long count = redisTemplate.execute(INCR_WITH_TTL, List.of(KEY_PREFIX + email), String.valueOf(BLOCK_TTL.getSeconds()));
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
