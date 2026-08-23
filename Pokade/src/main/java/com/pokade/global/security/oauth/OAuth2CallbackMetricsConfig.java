package com.pokade.global.security.oauth;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

@Configuration
public class OAuth2CallbackMetricsConfig {

    private static final String CALLBACK_URL_PATTERN = "/api/oauth2/callback/*";

    // 외부 IdP 왕복(인가 코드 교환 + 사용자 정보 조회)이 포함된 구간이라 조회성 API의 밀리초 버킷을
    // 쓰면 전부 최상위 버킷에 몰린다. 초 단위로 잡는다.

    private static final double[] CALLBACK_SLO_NANOS = {
            Duration.ofMillis(500).toNanos(),
            Duration.ofSeconds(1).toNanos(),
            Duration.ofSeconds(2).toNanos(),
            Duration.ofSeconds(5).toNanos(),
    };

    @Bean
    public FilterRegistrationBean<OAuth2CallbackTimingFilter> oauth2CallbackTimingFilterRegistration(
            MeterRegistry meterRegistry) {
        FilterRegistrationBean<OAuth2CallbackTimingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OAuth2CallbackTimingFilter(meterRegistry));
        registration.addUrlPatterns(CALLBACK_URL_PATTERN);
        //Security 필터체인(-100)보다 먼저 서야 코드 교환, 사용자 정보 조회까지 시간에 포함된다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // 콜백 타이머에만 SLO 버킷을 붙인다. 이름이 http.server.requests와 분리돼 있어 타입 충돌이 없고,
    // 버킷이면 histogram_quantile로 인스턴스 간 집계가 되므로 blue-green 두 슬롯에서도 합산된다.
    @Bean
    public MeterFilter oauth2CallbackTimingFilterMeterFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getType() != Meter.Type.TIMER || !OAuth2Metrics.CALLBACK_TIMER.equals(id.getName())) {
                    return config;
                }
                return DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(CALLBACK_SLO_NANOS)
                        .build()
                        .merge(config);
            }
        };
    }
}
