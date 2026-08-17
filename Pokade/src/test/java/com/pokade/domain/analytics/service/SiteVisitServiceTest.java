package com.pokade.domain.analytics.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiteVisitServiceTest {

    @Test
    @DisplayName("recordVisit 호출마다 site.visits 카운터가 1씩 증가한다")
    void recordVisit_incrementsCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SiteVisitService siteVisitService = new SiteVisitService(meterRegistry);

        siteVisitService.recordVisit();
        siteVisitService.recordVisit();

        assertThat(meterRegistry.counter("site.visits").count()).isEqualTo(2.0);
    }
}
