package com.pokade.global.security.oauth;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2CallbackTimingFilterTest {

    @Test
    @DisplayName("t1 콜백 요청의 소요시간을 provider 태그와 함께 기록한다")
    void t1() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuth2CallbackTimingFilter filter = new OAuth2CallbackTimingFilter(registry);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/oauth2/callback/google"),
                new MockHttpServletResponse(), Mockito.mock(FilterChain.class));

        Timer timer = registry.find("auth.oauth2.callback.duration").tag("provider", "google").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("t2 등록되지 않은 provider 경로는 unknown 하나로 접어 카디널리티를 묶는다")
    void t2() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuth2CallbackTimingFilter filter = new OAuth2CallbackTimingFilter(registry);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/oauth2/callback/anything"),
                new MockHttpServletResponse(), Mockito.mock(FilterChain.class));
        filter.doFilter(new MockHttpServletRequest("GET", "/api/oauth2/callback/local"),
                new MockHttpServletResponse(), Mockito.mock(FilterChain.class));

        assertThat(registry.find("auth.oauth2.callback.duration").tag("provider", "unknown").timer().count())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("t3 콜백 경로에만 스코프되고 Security 필터체인보다 먼저 실행되도록 등록된다")
    void t3() {
        FilterRegistrationBean<OAuth2CallbackTimingFilter> registration =
                new OAuth2CallbackMetricsConfig().oauth2CallbackTimingFilterRegistration(new SimpleMeterRegistry());

        assertThat(registration.getUrlPatterns()).containsExactly("/api/oauth2/callback/*");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}