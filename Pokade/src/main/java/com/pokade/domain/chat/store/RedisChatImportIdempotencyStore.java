package com.pokade.domain.chat.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

// 히스토리 이관 항목의 멱등성을 Redis TTL 키로 관리한다. 이관 대상 자체가 askedAt 기준 24시간
// 이내로만 허용되므로, 멱등성 마커도 그 이상 오래 보관할 필요가 없다 - Postgres에 영구 테이블로
// 두면 시간이 지나도 아무도 조회하지 않는 row가 계속 쌓이는데, TTL 키는 만료되면 Redis가 알아서 지운다.
@Repository
@RequiredArgsConstructor
public class RedisChatImportIdempotencyStore implements ChatImportIdempotencyStore {

    private static final String KEY_PREFIX = "chat-import-done:";

    // 이관 허용 윈도우(24시간)보다 넉넉하게 잡아 경계 근처 요청도 안전하게 덮는다.
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean markIfAbsent(String key) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", TTL);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
