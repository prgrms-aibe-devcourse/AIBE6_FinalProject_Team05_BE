package com.pokade.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisTokenBlacklistStore implements TokenBlacklistStore {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    // 유저를 블랙리스트에 등록 (TTL = access 만료 - 그 뒤엔 access가 자연 만료라 불필요)
    @Override
    public void blacklist(Long userId) {
        redisTemplate.opsForValue().set(
                BLACKLIST_KEY_PREFIX + userId, "1", jwtProperties.accessExpiration()
        );
    }

    // 블랙리스트 등록 여부
    @Override
    public boolean contains(Long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + userId));
        } catch (DataAccessException e) {
            log.warn("블랙리스트 조회 실패 - fail-open 처리 (userId={})", userId, e);
            return false;
        }
    }
}
