package com.pokade.global.security.oauth;

import com.pokade.global.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.Duration;
import java.util.Base64;

@Component
public class CookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String COOKIE_PATH = "/api/oauth2";
    private static final Duration COOKIE_TTL = Duration.ofMinutes(3);
    // 역직렬화 화이트리스트 - 쿠키로 들어온 임의 클래스 역질렬화(가젯 공격) 차단
    private static final String DESERIALIZE_FILTER = "org.springframework.security.oauth2.**;java.util.**;java.lang.**;java.time.**;java.net.**;!*";
    private static final String CLAIM_AUTH_REQUEST = "authRequest";

    private final boolean secure;
    private final JwtTokenProvider jwtTokenProvider;

    public CookieAuthorizationRequestRepository(
            @Value("${app.cookie.secure:false}") boolean secure,
            JwtTokenProvider jwtTokenProvider) {
        this.secure = secure;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = findCookie(request);
        return cookie == null ? null : deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            writeCookie(response, "", Duration.ZERO);
            return;
        }
        writeCookie(response, serialize(authorizationRequest), COOKIE_TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            writeCookie(response, "", Duration.ZERO);
        }
        return authorizationRequest;
    }

    // 요청에서 인가요청 쿠키를 찾는다
    private Cookie findCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie;
        }
        return null;
    }

    // 인가요청 쿠키를 응답에 심는다 (maxAge=ZERO면 만료)
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

    // OAuth2AuthorizationRequest -> Base64 문자열
    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try (ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
             ObjectOutputStream objectWriter = new ObjectOutputStream(byteBuffer)) {
            objectWriter.writeObject(authorizationRequest);
            objectWriter.flush();
            String base64 = Base64.getUrlEncoder().encodeToString(byteBuffer.toByteArray());
            return jwtTokenProvider.createSignedTicket(CLAIM_AUTH_REQUEST, base64, COOKIE_TTL);
        } catch (IOException e) {
            throw new IllegalStateException("OAuth2 인가요청 직렬화 실패", e);
        }
    }

    // Base64(URL) 문자열 ->  OAuth2AuthorizationRequest (화이트리스트 필터 적용)
    private OAuth2AuthorizationRequest deserialize(String value) {
        String base64 = jwtTokenProvider.parseSignedTicket(value, CLAIM_AUTH_REQUEST);
        if (base64 == null) {
            return null; // 서명 위조,만료 -> 문 앞에서 거부 (readObject 도달 안 함)
        }
        byte[] bytes = Base64.getUrlDecoder().decode(base64);
        try (ObjectInputStream objectReader = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            objectReader.setObjectInputFilter(ObjectInputFilter.Config.createFilter(DESERIALIZE_FILTER));
            return (OAuth2AuthorizationRequest) objectReader.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
