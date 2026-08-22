package com.pokade.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 슬라이스 테스트에 MeterRegistry 빈을 제공한다.
 *
 * <p>@WebMvcTest/@DataJpaTest 같은 슬라이스는 MetricsAutoConfiguration을 로드하지 않아
 * MeterRegistry 빈이 없다. 그래서 계측이 들어간 빈을 @Import로 올리면 컨텍스트 로딩 단계에서
 * NoSuchBeanDefinitionException으로 실패한다.
 *
 * <p>예전에는 각 서비스가 {@code @Autowired(required = false)} + {@code new SimpleMeterRegistry()}
 * 기본값으로 이 문제를 피했는데, 그러면 필드 주입이라 final을 못 쓰고 "테스트 때문에 프로덕션
 * 주입 방식을 바꾼" 모양이 된다. 그 회피책을 걷어내고 부족한 빈을 테스트 쪽에서 채우도록 뒤집은 것이
 * 이 클래스다(#343).
 *
 * <p><b>사용법</b> - 계측이 들어간 빈을 @Import 하는 슬라이스 테스트에 함께 추가한다:
 * <pre>
 * &#64;DataJpaTest
 * &#64;Import({NotificationService.class, TestMetricsConfig.class})
 * class SomeSliceTest { ... }
 * </pre>
 *
 * <p>Spring 컨텍스트를 안 쓰고 {@code new}로 직접 만드는 단위 테스트는 이 설정이 필요 없다.
 * 생성자에 {@code new SimpleMeterRegistry()}를 넘기면 된다.
 *
 * <p>SimpleMeterRegistry를 쓰는 이유: 인메모리라 외부 의존이 없고, 테스트가 실제로 기록된 값을
 * 확인해야 할 때는 이 빈을 주입받아 {@code registry.counter("...").count()}로 읽을 수 있다
 * (SiteVisitServiceTest가 그렇게 쓴다).
 */
@TestConfiguration
public class TestMetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
