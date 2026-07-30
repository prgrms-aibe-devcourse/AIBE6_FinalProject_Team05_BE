package com.pokade.domain.auth.service;

import com.pokade.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String GRACE_KEY_PREFIX = "auth:refresh:grace:";
    private static final Duration GRACE_TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId, refreshToken, jwtProperties.refreshExpiration()
        );
    }

    @Override
    public String find(Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        redisTemplate.delete(GRACE_KEY_PREFIX + userId);
    }

    @Override
    public void saveGrace(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(GRACE_KEY_PREFIX + userId, refreshToken, GRACE_TTL);
    }

    @Override
    public String findGrace(Long userId) {
        return redisTemplate.opsForValue().get(GRACE_KEY_PREFIX + userId);
    }

}
