package com.pokade.domain.card.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 카드 도메인 한정 임시 Rate Limit 필터 등록 설정.
 * FilterRegistrationBean으로 {@code /api/cards/*} 에만 스코프를 강제해,
 * SecurityConfig(공유 설정)를 변경하지 않고도 다른 도메인 API에 영향 없이 적용한다.
 * 전체 서비스 공통 Rate Limiter 정책이 팀 논의로 확정되면 이 설정과
 * {@link CardRateLimitFilter}는 함께 제거되어야 한다.
 */
@Configuration
public class CardRateLimitFilterConfig {

    @Bean
    public FilterRegistrationBean<CardRateLimitFilter> cardRateLimitFilterRegistration() {
        FilterRegistrationBean<CardRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CardRateLimitFilter());
        registration.addUrlPatterns("/api/cards/*");
        // Spring Security 필터체인보다 먼저 실행되어, 초과 요청은 JWT 인증 이전에 차단된다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
