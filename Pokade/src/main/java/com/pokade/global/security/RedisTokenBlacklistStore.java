package com.pokade.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisTokenBlacklistStore implements TokenBlacklistStore {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    // 유저를 블랙리스트에 등록 (TTL = access 만료 - 그 뒤엔 ccess가 자연 만료라 불필요)
    @Override
    public void blacklist(Long userId) {
        redisTemplate.opsForValue().set(
                BLACKLIST_KEY_PREFIX + userId, "1", jwtProperties.accessExpiration()
        );
    }

    // 블랙리스트 등록 여부
    @Override
    public boolean contains(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + userId));
    }
}
