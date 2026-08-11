package com.pokade.global.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.Duration;
import java.util.Base64;

// authorization request를 Redis에 저장(대용량 4KB 문제 회피) 쿠키엔 CSRF 바인딩용 state만
@Component
public class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_state";
    private static final String COOKIE_PATH = "/api/oauth2";
    private static final String REDIS_KEY_PREFIX = "oauth2:authreq:";
    private static final Duration TTL = Duration.ofMinutes(3);
    // 역직렬화 화이트리스트 - 임의 클래스 역직렬화(가젯 공격) 방어(서버측이라도 유지)
    private static final String DESERIALIZE_FILTER =
            "org.springframework.security.oauth2.**;java.util.**;java.lang.**;java.time.**;java.net.**;!*";

    private final boolean secure;
    private final StringRedisTemplate redisTemplate;

    public RedisAuthorizationRequestRepository(
            @Value("${app.cookie.secure:false}") boolean secure,
            StringRedisTemplate redisTemplate) {
        this.secure = secure;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            return; // 정리는 removeAuthorizationRequest가 담당, 키는 TTL로 자정
        }
        String state = authorizationRequest.getState();
        redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + state, serialize(authorizationRequest), TTL);
        writeCookie(response, state, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = readStateCookie(request); // 브라우저 바인딩: 파라미터 아닌 쿠키의 state로만 조회(CSRF)
        if (state == null) {
            return null;
        }
        String payload = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + state);
        return payload == null ? null : deserialize(payload);
    }


    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = readStateCookie(request);
        if (state == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        redisTemplate.delete(REDIS_KEY_PREFIX + state);
        writeCookie(response, "", Duration.ZERO);
        return authorizationRequest;
    }

    private String readStateCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buf)) {
            out.writeObject(authorizationRequest);
            out.flush();
            return Base64.getUrlEncoder().encodeToString(buf.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("OAuth2 인가요청 직렬화 실패", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(DESERIALIZE_FILTER));
            return (OAuth2AuthorizationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
