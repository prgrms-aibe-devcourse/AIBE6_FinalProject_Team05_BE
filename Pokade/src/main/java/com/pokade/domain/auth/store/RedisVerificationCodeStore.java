package com.pokade.domain.auth.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY_PREFIX = "auth:verify:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:verify:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:verify:attempt:";
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 or redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "return c", Long.class);
    private static final RedisScript<Long> SAVE_WITH_COOLDOWN = RedisScript.of(
            "if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then " +
                    "  redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3]) " +
                    "  redis.call('DEL', KEYS[3]) " +
                    "  return 1 " +
                    "else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean save(String email, String code) {
        Long result = redisTemplate.execute(SAVE_WITH_COOLDOWN,
                List.of(COOLDOWN_KEY_PREFIX + email, CODE_KEY_PREFIX + email, ATTEMPT_KEY_PREFIX + email),
                code,
                String.valueOf(COOLDOWN_TTL.getSeconds()),
                String.valueOf(CODE_TTL.getSeconds()));
        return result != null && result == 1L;
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
        redisTemplate.execute(INCR_WITH_TTL,
                List.of(ATTEMPT_KEY_PREFIX + email),
                String.valueOf(CODE_TTL.getSeconds()));
    }

    @Override
    public void delete(String email) {
        redisTemplate.delete(CODE_KEY_PREFIX + email);
        redisTemplate.delete(COOLDOWN_KEY_PREFIX + email);
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
    }
}
