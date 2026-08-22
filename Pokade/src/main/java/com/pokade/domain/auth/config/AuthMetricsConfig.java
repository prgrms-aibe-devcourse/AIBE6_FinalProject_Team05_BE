package com.pokade.domain.auth.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 인증·프로필 URI에만 응답시간 퍼센타일을 붙인다. http.server.requests는 모든 도메인이 공유하는
// 지표라 application.yaml에 분포 설정을 걸면 카드·워치리스트 등에도 그대로 적용된다. 적정 기준은
// 도메인마다 다르므로(로그인 117ms, 카드 검색 13.6ms) 여기서 URI로 범위를 좁힌다.
//
// 버킷(serviceLevelObjectives/percentilesHistogram)을 쓰지 않는 이유: 버킷을 붙이면 그 URI만
// Prometheus 타입이 Summary에서 Histogram으로 바뀐다. 같은 이름의 지표는 타입이 하나여야 하는데
// 일부 URI만 Histogram이 되면 스크랩 시 ClassCastException이 나 /actuator/prometheus가 500이 된다.
// percentiles는 Summary를 유지한 채 quantile 라벨만 더하므로 URI 단위 적용이 가능하다.
// 대신 발행한 퍼센타일만 조회할 수 있고 소급 계산은 안 된다. 소급 조회가 필요해지면
// http.server.requests가 아니라 auth 전용 지표 이름을 만들어 거기에 버킷을 붙이는 쪽으로 간다.
@Configuration
public class AuthMetricsConfig {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    private static final String[] TARGET_URI_PREFIXES = {"/api/auth", "/api/users"};

    // 인증·프로필 URI의 http.server.requests에만 p50·p95를 추가한다.
    @Bean
    public MeterFilter authRequestDistributionFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (!HTTP_SERVER_REQUESTS.equals(id.getName()) || !isTargetUri(id.getTag("uri"))) {
                    return config;
                }
                return DistributionStatisticConfig.builder()
                        .percentiles(0.5, 0.95)
                        .build()
                        .merge(config);
            }
        };
    }

    private static boolean isTargetUri(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefix : TARGET_URI_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}