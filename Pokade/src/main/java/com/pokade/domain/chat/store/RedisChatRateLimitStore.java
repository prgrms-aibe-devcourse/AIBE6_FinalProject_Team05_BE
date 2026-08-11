package com.pokade.domain.chat.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisChatRateLimitStore implements ChatRateLimitStore {

    private static final String LAST_MESSAGE_KEY_PREFIX = "chat:repeat:msg:";
    private static final String COUNT_KEY_PREFIX = "chat:repeat:count:";
    private static final String LOCKOUT_KEY_PREFIX = "chat:lockout:";

    // 반복 카운트를 유지하는 창 - 이 시간 동안 같은 메시지가 안 오면 카운트가 자연히 사라짐
    private static final Duration REPEAT_WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCKOUT_TTL = Duration.ofSeconds(60);

    // 직전 메시지(KEYS[1])와 이번 메시지(ARGV[1])를 비교해 같으면 카운트(KEYS[2]) +1, 다르면 1로 리셋 - 원자 실행
    private static final RedisScript<Long> COMPARE_AND_COUNT = RedisScript.of(
            "local last = redis.call('GET', KEYS[1]) " +
                    "local count " +
                    "if last == ARGV[1] then " +
                    "  count = redis.call('INCR', KEYS[2]) " +
                    "else " +
                    "  redis.call('SET', KEYS[1], ARGV[1]) " +
                    "  redis.call('SET', KEYS[2], 1) " +
                    "  count = 1 " +
                    "end " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[2]) " +
                    "return count", Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isLocked(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCKOUT_KEY_PREFIX + sessionId));
    }

    @Override
    public long recordAndCount(String sessionId, String message) {
        Long count = redisTemplate.execute(COMPARE_AND_COUNT,
                List.of(LAST_MESSAGE_KEY_PREFIX + sessionId, COUNT_KEY_PREFIX + sessionId),
                message,
                String.valueOf(REPEAT_WINDOW.getSeconds()));
        return count != null ? count : 1;
    }

    @Override
    public void lock(String sessionId) {
        redisTemplate.opsForValue().set(LOCKOUT_KEY_PREFIX + sessionId, "1", LOCKOUT_TTL);
    }
}
