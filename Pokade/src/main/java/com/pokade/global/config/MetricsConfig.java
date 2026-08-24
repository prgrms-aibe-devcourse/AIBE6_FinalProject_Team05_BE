package com.pokade.global.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

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
 *   <li>chat - ChatService (LLM 호출 시간, #358)</li>
 * </ul>
 *
 * <p>원래 domain/card/config에 있었는데(#343), 전역 효력을 가진 빈이 한 도메인 아래 숨어 있으면
 * 다른 도메인 담당자가 존재를 모른 채 지우거나 옮길 위험이 있어 global/config로 옮겼다.
 * 참조하는 코드는 없다 - 컴포넌트 스캔으로만 등록되므로 패키지를 옮겨도 호출부 변경이 필요 없다.
 */
@Configuration
public class MetricsConfig {

    // 조회성 API - 로컬 실측 평균이 13~18ms라(card.search 14.3ms, price.ranking 12.9ms, 10회 샘플)
    // 50ms를 첫 구간으로 잡으면 정상 트래픽이 최하위 버킷에 뭉치지 않는다. 200ms 경계는
    // "200ms 이내 요청 비율"을 PromQL로 바로 뽑기 위한 것이고, 1s는 이상치 탐지용이다.
    //
    // 주의: 200ms는 Grafana 대시보드의 "SLO 달성률" 패널이 le="0.2"로 하드코딩해 참조한다
    // (observability/grafana/dashboards/{card-domain,watchlist-notification}.json).
    // 이 값을 바꾸면 해당 시리즈가 사라져 패널이 조용히 "No data"가 되므로 대시보드도 함께 고칠 것.
    // 경계값은 나노초로 선언하지만 Prometheus에는 초 단위 le 라벨로 노출된다(200ms -> le="0.2").
    private static final double[] QUERY_API_SLO_NANOS = {
            Duration.ofMillis(50).toNanos(),
            Duration.ofMillis(100).toNanos(),
            Duration.ofMillis(200).toNanos(),
            Duration.ofMillis(500).toNanos(),
            Duration.ofSeconds(1).toNanos(),
    };

    // AI 등급 진단은 S3 업로드 6장 + OpenAI Vision 실호출이라 초 단위가 정상이다. 조회성 버킷을
    // 쓰면 전부 최상위 버킷에 몰려 히스토그램이 무의미해진다.
    // 주의: 이 값은 실측이 아니라 추정이다(#343 작업 시점에 ai.grade 호출 이력이 없어 데이터가 없었다).
    // 실제 진단이 쌓인 뒤 분포를 보고 조정할 것.
    private static final double[] AI_GRADE_SLO_NANOS = {
            Duration.ofSeconds(2).toNanos(),
            Duration.ofSeconds(5).toNanos(),
            Duration.ofSeconds(10).toNanos(),
            Duration.ofSeconds(30).toNanos(),
            Duration.ofSeconds(60).toNanos(),
    };

    private static final Set<String> QUERY_API_TIMERS = Set.of(
            "card.search.duration",
            "card.search.keyword.duration",
            "watchlist.add.duration",
            "watchlist.update.duration",
            "price.ranking.duration");

    // 외부 LLM 호출이라 초 단위가 정상인 타이머들. chat.llm.duration은 ai.grade.duration과 특성이
    // 같아(네트워크로 LLM 호출) 같은 버킷을 공유한다 - 별도 버킷이 필요해지면 그때 분리한다.
    private static final Set<String> AI_GRADE_TIMERS = Set.of(
            "ai.grade.duration",
            "chat.llm.duration");

    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }

    /**
     * @Timed 타이머에 SLO 버킷을 붙인다. 이게 없으면 count/sum/max만 노출돼서
     * "응답시간이 200ms 이내인 요청이 몇 %인지" 같은 걸 Prometheus에서 계산할 수 없다.
     *
     * <p>@Timed 애노테이션 자체에도 serviceLevelObjectives 속성이 있지만 그 방식은 지표가 선언된
     * 6개 서비스 파일을 전부 고쳐야 한다(그중 둘은 다른 도메인 담당자 소유다). 여기서 이름으로
     * 매칭하면 선언부를 건드리지 않고 버킷만 얹을 수 있어 이 방식을 택했다.
     *
     * <p>Spring Boot 3.x의 management.metrics.distribution.slo.* 프로퍼티는 Boot 4에서 제거됐다
     * (설정 메타데이터에 management.metrics.* 자체가 없다) - yaml로는 지정할 수 없어 코드로 둔다.
     *
     * <p>SLO 경계값 단위에 주의: Timer의 DistributionStatisticConfig는 나노초 기준이라
     * Duration.toNanos()로 변환해서 넘긴다.
     */
    @Bean
    public MeterFilter timedSloFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                double[] slos = resolveServiceLevelObjectives(id);
                if (slos == null) {
                    return config;
                }
                // merge(config): 기존 설정을 살리고 SLO만 덧입힌다 - 다른 필터나 기본값을 덮어쓰지 않는다.
                return DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(slos)
                        .build()
                        .merge(config);
            }
        };
    }

    // 타이머가 아닌 미터(Counter/Gauge 등)에는 SLO 개념이 없으므로 타입까지 확인한다.
    private static double[] resolveServiceLevelObjectives(Meter.Id id) {
        if (id.getType() != Meter.Type.TIMER) {
            return null;
        }
        if (QUERY_API_TIMERS.contains(id.getName())) {
            return QUERY_API_SLO_NANOS;
        }
        if (AI_GRADE_TIMERS.contains(id.getName())) {
            return AI_GRADE_SLO_NANOS;
        }
        return null;
    }
}
