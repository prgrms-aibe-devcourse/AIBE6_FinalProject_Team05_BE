package com.pokade.domain.auth.service;

import com.pokade.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String GRACE_KEY_PREFIX = "auth:refresh:grace:";
    private static final Duration GRACE_TTL = Duration.ofSeconds(5);
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    // refresh 토큰을 해시로 저장 (원문 미보관)
    @Override
    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId, hash(refreshToken), jwtProperties.refreshExpiration()
        );
    }

    //refresh 키 존재 여부
    @Override
    public boolean exists(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_KEY_PREFIX + userId));
    }

    // 제시된 토큰의 해시가 저장된 refresh 해시와 일치하는지
    @Override
    public boolean matches(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        return stored != null && stored.equals(hash(refreshToken));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        redisTemplate.delete(GRACE_KEY_PREFIX + userId);
    }

    // 직전 refresh를 grace 창(5초) 동안 해시로 보관
    @Override
    public void saveGrace(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(GRACE_KEY_PREFIX + userId, hash(refreshToken), GRACE_TTL);
    }

    // 제시된 토큰의 해시가 grace 해시와 일치하는지
    @Override
    public boolean matchesGrace(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(GRACE_KEY_PREFIX + userId);
        return stored != null && stored.equals(hash(refreshToken));
    }

    // SHA-256 해시 -> 16진 문자열 (외부 의존성 없이 JDK만)
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
