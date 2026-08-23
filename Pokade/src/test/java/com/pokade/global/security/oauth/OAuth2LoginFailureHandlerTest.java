package com.pokade.global.security.oauth;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    private static final String REDIRECT_BASE = "http://localhost:3000";

    @Test
    @DisplayName("t1 인증 실패를 provider·failure 태그로 집계하고 사유와 함께 로그인 페이지로 돌려보낸다")
    void t1() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(registry, REDIRECT_BASE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest("GET", "/api/oauth2/callback/google"),
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(registry.find("auth.oauth2.result")
                .tag("provider", "google")
                .tag("result", "failure")
                .counter().count()).isEqualTo(1);
        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_BASE + "/login?error=access_denied");
    }

    @Test
    @DisplayName("t2 등록되지 않은 provider 경로는 unknown 하나로 접는다")
    void t2() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(registry, REDIRECT_BASE);

        handler.onAuthenticationFailure(
                new MockHttpServletRequest("GET", "/api/oauth2/callback/anything"),
                new MockHttpServletResponse(),
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(registry.find("auth.oauth2.result")
                .tag("provider", "unknown")
                .tag("result", "failure")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("t3 OAuth2 예외가 아니면 사유를 알 수 없으므로 기본 사유로 돌려보낸다")
    void t3() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(registry, REDIRECT_BASE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest("GET", "/api/oauth2/callback/kakao"),
                response,
                new BadCredentialsException("자격 증명 실패"));

        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_BASE + "/login?error=oauth2_failed");
        assertThat(registry.find("auth.oauth2.result")
                .tag("provider", "kakao")
                .tag("result", "failure")
                .counter().count()).isEqualTo(1);
    }
}
