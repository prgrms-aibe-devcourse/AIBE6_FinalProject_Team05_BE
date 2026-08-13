package com.pokade.domain.auth.store;

import com.pokade.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String GRACE_KEY_PREFIX = "auth:refresh:grace:";
    private static final Duration GRACE_TTL = Duration.ofSeconds(5);

    // 현재 refresh와 일치할 때만 원자적으로 회전: 옛 current→grace, 새 refresh→current (동시요청 double-rotate 차단)
    private static final RedisScript<Long> COMPARE_AND_ROTATE = RedisScript.of(
            "local cur = redis.call('GET', KEYS[1]) " +
                    "if cur == ARGV[1] then " +
                    "  redis.call('SET', KEYS[2], cur, 'EX', ARGV[3]) " +
                    "  redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[4]) " +
                    "  return 1 " +
                    "end " +
                    "return 0", Long.class);

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
    public boolean compareAndRotate(Long userId, String presentedToken, String newRefreshToken) {
        Long rotated = redisTemplate.execute(COMPARE_AND_ROTATE,
                List.of(REFRESH_KEY_PREFIX + userId, GRACE_KEY_PREFIX + userId),
                hash(presentedToken),
                hash(newRefreshToken),
                String.valueOf(GRACE_TTL.getSeconds()),
                String.valueOf(jwtProperties.refreshExpiration().getSeconds()));
        return Long.valueOf(1L).equals(rotated);
    }

    // 제시된 토큰의 해시가 grace 해시와 일치하는지
    @Override
    public boolean matchesGrace(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(GRACE_KEY_PREFIX + userId);
        return stored != null && stored.equals(hash(refreshToken));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        redisTemplate.delete(GRACE_KEY_PREFIX + userId);
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

    private String key(Long userId, String sid) {
        return REFRESH_KEY_PREFIX + userId + ":" + sid;
    }

    private String graceKey(Long userId, String sid) {
        return GRACE_KEY_PREFIX + userId + ":" + sid;
    }

    @Override
    public void save(Long userId, String sid, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId, sid), hash(refreshToken), jwtProperties.refreshExpiration());
    }

    @Override
    public boolean exists(Long userId, String sid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId, sid)));
    }

    @Override
    public boolean compareAndRotate(Long userId, String sid, String presentedToken, String newRefreshToken) {
        Long rotated = redisTemplate.execute(COMPARE_AND_ROTATE,
                List.of(key(userId, sid), graceKey(userId, sid)),
                hash(presentedToken), hash(newRefreshToken),
                String.valueOf(GRACE_TTL.getSeconds()),
                String.valueOf(jwtProperties.refreshExpiration().getSeconds()));
        return Long.valueOf(1L).equals(rotated);
    }

    @Override
    public boolean matchesGrace(Long userId, String sid, String refreshToken) {
        String store = redisTemplate.opsForValue().get(graceKey(userId, sid));
        return store != null && store.equals(hash(refreshToken));
    }

    @Override
    public void delete(Long userId, String sid) {
        redisTemplate.delete(graceKey(userId, sid));
        redisTemplate.delete(key(userId, sid));
    }

    @Override
    public void deleteAll(Long userId) {
        deleteByPattern(REFRESH_KEY_PREFIX + userId + ":*");
        deleteByPattern(GRACE_KEY_PREFIX + userId + ":*");
    }

    private void deleteByPattern(String pattern) {
        var options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
            }
        }
    }
}
