package com.pokade.global.config;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pokade.domain.card.service.CardQueryService;
import com.pokade.domain.watchlist.service.WatchlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SLO 버킷이 실제로 붙는지 검증한다.
 *
 * <p>{@link MetricsConfig}의 SLO 대상은 지표 "이름 문자열"로 매칭한다. 그래서 누군가
 * {@code @Timed}의 value를 바꾸면 컴파일도 기존 테스트도 통과하는데 버킷만 조용히 사라진다.
 * 이 테스트는 애노테이션에서 이름을 리플렉션으로 읽어와 필터에 넣어보는 방식이라, 그런 리네임을
 * 실패로 잡아낸다.
 *
 * <p><b>소유권 주의</b> - 본인 소유 도메인(card/watchlist)만 실제 클래스를 참조해 애노테이션을 읽는다.
 * price/ai의 타이머는 다른 담당자 소유라 클래스를 건드리지 않고 이름 문자열로만 확인한다
 * (그쪽에서 리네임하면 이 테스트는 못 잡는다 - 의도된 한계다).
 */
class MetricsConfigTest {

    private static final double[] EXPECTED_QUERY_API_SLO_NANOS = {
            Duration.ofMillis(50).toNanos(),
            Duration.ofMillis(100).toNanos(),
            Duration.ofMillis(200).toNanos(),
            Duration.ofMillis(500).toNanos(),
            Duration.ofSeconds(1).toNanos(),
    };

    private static final double[] EXPECTED_AI_GRADE_SLO_NANOS = {
            Duration.ofSeconds(2).toNanos(),
            Duration.ofSeconds(5).toNanos(),
            Duration.ofSeconds(10).toNanos(),
            Duration.ofSeconds(30).toNanos(),
            Duration.ofSeconds(60).toNanos(),
    };

    @Test
    @DisplayName("card 도메인의 @Timed 이름이 전부 조회성 SLO 버킷을 받는다")
    void cardTimers_haveQueryApiSloBuckets() {
        Set<String> timerNames = timedValuesOf(CardQueryService.class);

        // 계측 자체가 통째로 사라졌는데 테스트는 통과하는 상황을 막는다.
        assertThat(timerNames).isNotEmpty();
        timerNames.forEach(name ->
                assertThat(sloBoundariesOf(name))
                        .as("%s 의 SLO 버킷", name)
                        .containsExactly(EXPECTED_QUERY_API_SLO_NANOS));
    }

    @Test
    @DisplayName("watchlist 도메인의 @Timed 이름이 전부 조회성 SLO 버킷을 받는다")
    void watchlistTimers_haveQueryApiSloBuckets() {
        Set<String> timerNames = timedValuesOf(WatchlistService.class);

        assertThat(timerNames).isNotEmpty();
        timerNames.forEach(name ->
                assertThat(sloBoundariesOf(name))
                        .as("%s 의 SLO 버킷", name)
                        .containsExactly(EXPECTED_QUERY_API_SLO_NANOS));
    }

    // price/ai는 다른 담당자 소유라 클래스를 참조하지 않고 이름만 확인한다.
    @Test
    @DisplayName("타 도메인 타이머도 이름 기준으로 각자의 SLO 버킷을 받는다")
    void otherDomainTimers_haveSloBucketsByName() {
        assertThat(sloBoundariesOf("price.ranking.duration"))
                .containsExactly(EXPECTED_QUERY_API_SLO_NANOS);
        assertThat(sloBoundariesOf("ai.grade.duration"))
                .containsExactly(EXPECTED_AI_GRADE_SLO_NANOS);
    }

    @Test
    @DisplayName("SLO 대상이 아닌 타이머에는 버킷이 붙지 않는다")
    void unrelatedTimer_hasNoSloBuckets() {
        assertThat(sloBoundariesOf("some.other.duration")).isEmpty();
    }

    @Test
    @DisplayName("이름이 SLO 대상과 같아도 타이머가 아니면 버킷이 붙지 않는다")
    void counterWithTimerName_hasNoSloBuckets() {
        SimpleMeterRegistry registry = registryWithSloFilter();

        Counter counter = registry.counter("card.search.duration");

        assertThat(counter.getId().getType()).isEqualTo(Meter.Type.COUNTER);
        assertThat(registry.find("card.search.duration").timer()).isNull();
    }

    private static Set<String> timedValuesOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(method -> method.getAnnotation(Timed.class))
                .filter(Objects::nonNull)
                .map(Timed::value)
                .collect(Collectors.toSet());
    }

    // 실제 레지스트리에 타이머를 만들어, 필터가 적용된 뒤의 히스토그램 경계를 읽는다.
    private static double[] sloBoundariesOf(String timerName) {
        Timer timer = registryWithSloFilter().timer(timerName);
        return Arrays.stream(timer.takeSnapshot().histogramCounts())
                .mapToDouble(CountAtBucket::bucket)
                .toArray();
    }

    private static SimpleMeterRegistry registryWithSloFilter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricsConfig().timedSloFilter());
        return registry;
    }
}
