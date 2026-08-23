package com.pokade.global.security.oauth;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OAuth2 콜백 한 건의 전체 소요시간을 잰다. 인가 코드 교환과 사용자 정보 조회는 Security 필터체인
 * 안에서 일어나고 리다이렉트로 끝나므로 컨트롤러에 도달하지 않는다. 그래서 http.server.requests의
 * uri 태그로는 구분되지 않고, 이 필터가 체인 바깥에서 감싸야 구간 전체가 잡힌다.
 *
 * <p>URL 스코프와 실행 순서는 {@link OAuth2CallbackMetricsConfig}가 강제한다.
 */
public class OAuth2CallbackTimingFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public OAuth2CallbackTimingFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 인증 실패로 예외가 올라가도 소요시간은 남긴다.
            sample.stop(Timer.builder(OAuth2Metrics.CALLBACK_TIMER)
                    .tag(OAuth2Metrics.PROVIDER_TAG, OAuth2Metrics.providerTagFromUri(request.getRequestURI()))
                    .register(meterRegistry));
        }
    }
}
