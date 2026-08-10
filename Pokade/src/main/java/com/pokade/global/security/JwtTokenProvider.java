package com.pokade.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

// JWT access token 발급·검증을 담당하는 컴포넌트
@Component
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    // 시크릿 문자열로 서명용 SecretKey를 만들어 캐싱 (앱 기동 시 1회)
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    // access token 발급 (subject=userId, role 클레임을 담아 서명)
    public String createAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.accessExpiration().toMillis()))
                .signWith(secretKey)
                .compact();
    }

    // refresh token 발급 (subject=userId만, role 없이 최소 구성)
    public String createRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.refreshExpiration().toMillis()))
                .signWith(secretKey)
                .compact();
    }

    // 토큰의 서명·만료를 검증 (유효하면 true, 아니면 false)
    public boolean isValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 userId(subject)를 추출 (실패 시 null)
    public Long getUserId(String token) {
        try {
            return Long.parseLong(Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    // 토큰에서 role 클레임을 추출
    public String getRole(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    // 임의 값을 담은 단명 서명 티켓 발급 (범용 - 쿠키 인증요청, 가입 티켓 공용)
    public String createSignedTicket(String claim, String value, Duration ttl) {
        return Jwts.builder()
                .claim(claim, value)
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    // 단명 서명 티켓에서 값을 추출 (서명, 만료 검증 실패 시 null)
    public String parseSignedTicket(String token, String claim) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(claim, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    // 여러 값을 담은 단명 서명 티켓 발급 (밤용 - 가입 티켓 등)
    public String createSignedTicket(Map<String, String> claims, Duration ttl) {
        var builder = Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()));
        claims.forEach(builder::claim);
        return builder.signWith(secretKey).compact();
    }
}
