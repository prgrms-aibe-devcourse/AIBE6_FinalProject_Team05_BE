package com.pokade.global.web;

import com.pokade.global.security.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;
    private final Duration refreshExpiration;

    public RefreshTokenCookieFactory(
            @Value("${app.cookie.secure:false}") boolean secure,
            JwtProperties jwtProperties
    ) {
        this.secure = secure;
        this.refreshExpiration = jwtProperties.refreshExpiration();
    }

    public ResponseCookie create(String refreshToken) {
        return build(refreshToken, refreshExpiration);
    }

    public ResponseCookie expired() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }

}
