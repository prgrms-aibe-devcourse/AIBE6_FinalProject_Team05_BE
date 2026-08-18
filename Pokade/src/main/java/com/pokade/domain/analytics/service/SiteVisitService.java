package com.pokade.domain.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class SiteVisitService {

    private final Counter visitCounter;

    public SiteVisitService(MeterRegistry meterRegistry) {
        this.visitCounter = meterRegistry.counter("site.visits");
    }

    public void recordVisit() {
        visitCounter.increment();
    }
}
