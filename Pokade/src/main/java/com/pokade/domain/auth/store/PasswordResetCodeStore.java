package com.pokade.domain.auth.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PasswordResetCodeStore {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String CODE_KEY_PREFIX = "auth:reset:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:reset:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:reset:attempt:";
    private static final int MAX_ATTEMPTS = 5;
    private static final RedisScript<String> VERIFY_AND_CONSUME = RedisScript.of(
            "local attempts = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "if attempts >= tonumber(ARGV[2]) then return 'EXCEEDED' end " +
                    "local stored = redis.call('GET', KEYS[2]) " +
                    "if not stored then return 'EXPIRED' end " +
                    "if stored == ARGV[1] then " +
                    "  redis.call('DEL', KEYS[1], KEYS[2], KEYS[3]) " +
                    "  return 'OK' " +
                    "end " +
                    "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 or redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end " +
                    "return 'MISMATCH'", String.class);
    private static final RedisScript<Long> SAVE_WITH_COOLDOWN = RedisScript.of(
            "if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then " +
                    "  redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3]) " +
                    "  redis.call('DEL', KEYS[3]) " +
                    "  return 1 " +
                    "else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean save(String email, String code) {
        Long result = redisTemplate.execute(SAVE_WITH_COOLDOWN,
                List.of(COOLDOWN_KEY_PREFIX + email, CODE_KEY_PREFIX + email, ATTEMPT_KEY_PREFIX + email),
                code,
                String.valueOf(COOLDOWN_TTL.getSeconds()),
                String.valueOf(CODE_TTL.getSeconds()));
        return result != null && result == 1L;
    }

    public VerificationResult verifyAndConsume(String email, String code) {
        String result = redisTemplate.execute(VERIFY_AND_CONSUME,
                List.of(ATTEMPT_KEY_PREFIX + email, CODE_KEY_PREFIX + email, COOLDOWN_KEY_PREFIX + email),
                code,
                String.valueOf(MAX_ATTEMPTS),
                String.valueOf(CODE_TTL.getSeconds()));
        return VerificationResult.valueOf(result);
    }
}
