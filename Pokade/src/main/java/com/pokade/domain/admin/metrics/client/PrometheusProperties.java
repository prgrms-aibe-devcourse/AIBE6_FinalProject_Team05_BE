package com.pokade.domain.admin.metrics.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 기본값 없이는 슬라이스 테스트 컨텍스트 로딩이 실패한다(ScrydexProperties와 동일한 이유).
@ConfigurationProperties(prefix = "prometheus")
public record PrometheusProperties(
        @DefaultValue("http://localhost:9090") String baseUrl
) {
}
