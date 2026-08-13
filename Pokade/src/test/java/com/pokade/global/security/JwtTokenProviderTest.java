package com.pokade.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "pokade-local-dev-jwt-secret-key-change-in-prod-0123456789";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));

    @Test
    @DisplayName("발급한 토큰은 유효하고, userId와 role을 그대로 복원한다")
    void createAccessToken_isValidAndCarriesClaims() {
        String token = provider.createAccessToken(42L, "USER");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
        assertThat(provider.getRole(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("만료된 토큰은 isValid가 false를 반환한다")
    void isValid_returnsFalseForExpiredToken() {
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(14)));
        String expired = expiredProvider.createAccessToken(42L, "USER");

        assertThat(provider.isValid(expired)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명된(위조) 토큰은 isValid가 false를 반환한다")
    void isValid_returnsFalseForForgedToken() {
        JwtTokenProvider otherKeyProvider =
                new JwtTokenProvider(new JwtProperties("another-completely-different-secret-key-0123456789", Duration.ofMinutes(30), Duration.ofDays(14)));
        String forged = otherKeyProvider.createAccessToken(42L, "USER");

        assertThat(provider.isValid(forged)).isFalse();
    }

    @Test
    @DisplayName("형식이 잘못된 문자열은 isValid가 false를 반환한다")
    void isValid_returnsFalseForMalformedToken() {
        assertThat(provider.isValid("not-a-real-token")).isFalse();
    }

    @Test
    @DisplayName("refresh 토큰은 sid를 담고, userId·sid를 그대로 복원한다")
    void refreshToken_carriesSid_andExtractable() {
        String token = provider.createRefreshToken(1L, "sess-abc");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(1L);
        assertThat(provider.getSessionId(token)).isEqualTo("sess-abc");
    }

    @Test
    @DisplayName("access 토큰에는 sid가 없어 getSessionId가 null이다")
    void accessToken_hasNoSid() {
        String access = provider.createAccessToken(1L, "USER");

        assertThat(provider.getSessionId(access)).isNull();
    }
}
