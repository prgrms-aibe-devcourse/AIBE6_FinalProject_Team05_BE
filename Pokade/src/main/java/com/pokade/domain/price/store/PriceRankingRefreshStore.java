package com.pokade.domain.price.store;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

// 시세 랭킹(priceRanking 캐시)이 실제로 마지막에 계산된 시각을 별도로 기록한다 - 캐시 자체는
// 값만 들고 있어 "언제 채워졌는지"를 알 수 없다. TTL을 캐시(CacheConfig.PRICE_RANKING_CACHE)와
// 동일한 48시간으로 맞춰서, 캐시가 만료되면 이 기록도 함께 사라지게 한다.
@Repository
@RequiredArgsConstructor
public class PriceRankingRefreshStore {

    private static final String KEY_PREFIX = "priceRanking:refreshedAt:";
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;

    public void recordNow(String type, LocalDateTime now) {
        redisTemplate.opsForValue().set(KEY_PREFIX + type, now.toString(), TTL);
    }

    public LocalDateTime findRefreshedAt(String type) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + type);
        return value != null ? LocalDateTime.parse(value) : null;
    }
}
