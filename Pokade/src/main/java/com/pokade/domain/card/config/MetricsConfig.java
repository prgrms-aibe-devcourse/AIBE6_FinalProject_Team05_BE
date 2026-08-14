package com.pokade.domain.card.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator/Prometheus 로컬 실험용 설정 - 커밋 대상 아님.
 * TimedAspect Bean이 없으면 CardService의 @Timed 애노테이션이 조용히 무시된다.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }
}
