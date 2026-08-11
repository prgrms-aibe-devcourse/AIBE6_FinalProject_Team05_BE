package com.pokade.domain.sync.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 각 필드에 @DefaultValue를 둔 이유: JwtProperties(jwt.secret)와 달리 이 값들은 배치가 실제로 호출될 때만
 * 필요하다 - 값이 없어도 앱 부팅/테스트 컨텍스트 로딩 자체가 실패하면 안 된다(@ConfigurationPropertiesScan은
 * 사용 여부와 무관하게 스캔된 모든 프로퍼티 빈을 기동 시 바인딩하므로, 기본값이 없으면 scrydex.* 설정이
 * 없는 프로파일/테스트 컨텍스트에서 바인딩 자체가 실패한다).
 */
@ConfigurationProperties(prefix = "scrydex")
public record ScrydexProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("") String teamId,
        @DefaultValue("https://api.scrydex.com") String baseUrl,
        @DefaultValue("100") int pageSize
) {
}
