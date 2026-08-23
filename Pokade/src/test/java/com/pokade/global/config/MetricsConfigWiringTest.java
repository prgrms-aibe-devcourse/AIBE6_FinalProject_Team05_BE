package com.pokade.global.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MetricsConfig}의 두 빈이 <b>실제로 Spring에 등록되고 레지스트리에 적용되는지</b> 검증한다.
 *
 * <p>{@link MetricsConfigTest}는 필터 "로직"만 본다 - {@code new MetricsConfig().timedSloFilter()}를
 * 직접 호출하므로 {@code @Bean}을 떼어내도 그대로 통과한다. 그러면 프로덕션만 조용히 망가진다.
 * 특히 TimedAspect는 빈이 없으면 @Timed가 예외도 경고도 없이 무시되는데, 그 시나리오를 잡는 테스트가
 * 여기 생기기 전까지 하나도 없었다(#343).
 *
 * <p><b>왜 @SpringBootTest가 아니라 ApplicationContextRunner인가</b> - 이 저장소의 @SpringBootTest는
 * 기본 프로파일이 prod라 실제 Postgres/Redis와 .env가 있어야 뜬다(PokadeApplicationTests가 그렇다).
 * 빈 배선만 확인하는 데 그 의존성을 끌어올 이유가 없어, 필요한 자동설정 두 개만 올린다.
 */
class MetricsConfigWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class))
            .withUserConfiguration(MetricsConfig.class);

    @Test
    @DisplayName("TimedAspect 빈이 등록된다 - 없으면 @Timed가 조용히 무시된다")
    void timedAspect_isRegisteredAsBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TimedAspect.class));
    }

    // Boot가 자체 propertiesMeterFilter를 함께 등록하므로 MeterFilter 빈은 2개다 - hasSingleBean은 쓸 수 없다.
    @Test
    @DisplayName("SLO MeterFilter 빈이 등록된다")
    void sloFilter_isRegisteredAsBean() {
        contextRunner.run(context -> assertThat(context)
                .getBean("timedSloFilter", MeterFilter.class)
                .isNotNull());
    }

    /**
     * 빈으로 "존재"하는 것과 레지스트리에 "적용"되는 것은 다르다. 여기서는 컨텍스트가 만든 실제
     * MeterRegistry로 타이머를 만들어, Boot의 MeterRegistryPostProcessor가 필터를 실제로
     * 물려줬는지까지 확인한다.
     */
    @Test
    @DisplayName("MeterFilter가 컨텍스트의 MeterRegistry에 실제로 적용된다")
    void sloFilter_isAppliedToContextRegistry() {
        contextRunner.run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            Timer timer = registry.timer("card.search.duration");

            double[] boundaries = Arrays.stream(timer.takeSnapshot().histogramCounts())
                    .mapToDouble(CountAtBucket::bucket)
                    .toArray();
            // 대시보드 PromQL이 쓰는 le="0.2" 경계가 실제로 노출되는지까지 본다.
            assertThat(boundaries).contains(Duration.ofMillis(200).toNanos());
        });
    }

    @Test
    @DisplayName("SLO 대상이 아닌 타이머는 컨텍스트 레지스트리에서도 버킷을 받지 않는다")
    void unrelatedTimer_getsNoBucketsFromContextRegistry() {
        contextRunner.run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            Timer timer = registry.timer("some.other.duration");

            assertThat(timer.takeSnapshot().histogramCounts()).isEmpty();
        });
    }
}
