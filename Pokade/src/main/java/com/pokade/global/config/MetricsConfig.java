package com.pokade.global.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer @Timed를 실제로 동작시키는 전역 설정.
 *
 * <p>이 빈이 없으면 @Timed 애노테이션은 예외도 경고도 없이 그대로 무시된다 - 지표가 안 쌓이는데
 * 코드는 멀쩡해 보여서 알아차리기 어렵다. 영향 범위는 카드 도메인에 한정되지 않고 현재 네 도메인
 * 여섯 곳 전부다:
 * <ul>
 *   <li>card - CardQueryService.search() / searchByKeyword()</li>
 *   <li>watchlist - WatchlistService.addWatchlist() / updateWatchlist()</li>
 *   <li>ai - AiGradeService (등급 진단 전체 처리 시간)</li>
 *   <li>price - PriceService (랭킹 조회 시간)</li>
 * </ul>
 *
 * <p>원래 domain/card/config에 있었는데(#343), 전역 효력을 가진 빈이 한 도메인 아래 숨어 있으면
 * 다른 도메인 담당자가 존재를 모른 채 지우거나 옮길 위험이 있어 global/config로 옮겼다.
 * 참조하는 코드는 없다 - 컴포넌트 스캔으로만 등록되므로 패키지를 옮겨도 호출부 변경이 필요 없다.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }
}
