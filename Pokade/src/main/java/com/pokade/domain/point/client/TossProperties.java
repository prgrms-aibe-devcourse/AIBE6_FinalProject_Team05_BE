package com.pokade.domain.point.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 각 필드에 @DefaultValue를 둔 이유: ScrydexProperties와 동일 - 값이 없어도 앱 부팅/테스트 컨텍스트
// 로딩 자체가 실패하면 안 된다(@ConfigurationPropertiesScan은 사용 여부와 무관하게 기동 시 바인딩한다).
@ConfigurationProperties(prefix = "toss")
public record TossProperties(
        @DefaultValue("") String clientKey,
        @DefaultValue("") String secretKey,
        @DefaultValue("https://api.tosspayments.com") String baseUrl
) {
}
