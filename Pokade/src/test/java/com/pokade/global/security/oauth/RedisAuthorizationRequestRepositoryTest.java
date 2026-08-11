package com.pokade.global.security.oauth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisAuthorizationRequestRepositoryTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    RedisAuthorizationRequestRepository repo;

    @BeforeEach
    void setUp() {
        repo = new RedisAuthorizationRequestRepository(false, redisTemplate);
    }

    private OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost:8080/api/oauth2/callback/google")
                .scopes(Set.of("openid", "email", "profile"))
                .state("STATE123")
                .authorizationRequestUri("https://accounts.google.com/o/oauth2/v2/auth?state=STATE123")
                .build();
    }

    @Test
    @DisplayName("save: Redis에 state 키로 저장 + state 쿠키 세팅")
    void save_storesInRedisAndSetsStateCookie() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        MockHttpServletResponse res = new MockHttpServletResponse();

        repo.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), res);

        then(valueOps).should().set(eq("oauth2:authreq:STATE123"), anyString(), eq(Duration.ofMinutes(3)));
        assertThat(res.getHeader("Set-Cookie")).contains("oauth2_auth_state=STATE123");
    }

    @Test
    @DisplayName("load: 쿠키의 state로 조회해 역직렬화 왕복 성공")
    void load_roundTripViaCookie() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        repo.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), new MockHttpServletResponse());
        then(valueOps).should().set(anyString(), payload.capture(), any());

        given(valueOps.get("oauth2:authreq:STATE123")).willReturn(payload.getValue());
        MockHttpServletRequest loadReq = new MockHttpServletRequest();
        loadReq.setCookies(new Cookie("oauth2_auth_state", "STATE123"));

        OAuth2AuthorizationRequest loaded = repo.loadAuthorizationRequest(loadReq);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("STATE123");
        assertThat(loaded.getScopes()).contains("openid");
    }

    @Test
    @DisplayName("load: state 쿠키 없으면 null")
    void load_noCookie_returnsNull() {
        assertThat(repo.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("remove: Redis 삭제 + 쿠키 만료")
    void remove_deletesRedisAndClearsCookie() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        repo.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), new MockHttpServletResponse());
        then(valueOps).should().set(anyString(), payload.capture(), any());
        given(valueOps.get("oauth2:authreq:STATE123")).willReturn(payload.getValue());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("oauth2_auth_state", "STATE123"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repo.removeAuthorizationRequest(req, res);

        assertThat(removed).isNotNull();
        then(redisTemplate).should().delete("oauth2:authreq:STATE123");
        assertThat(res.getHeader("Set-Cookie")).contains("oauth2_auth_state=;").contains("Max-Age=0");
    }
}