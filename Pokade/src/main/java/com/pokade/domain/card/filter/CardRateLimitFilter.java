package com.pokade.domain.card.filter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.response.ApiResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 카드 도메인({@code /api/cards/**}) 한정 임시 IP 기준 Rate Limit 필터.
 * 전체 서비스 공통 Rate Limiter 정책이 팀 논의로 확정되면 이 필터는 제거되고
 * 공통 필터로 대체되어야 한다. URL 스코프는 {@link CardRateLimitFilterConfig}에서
 * {@code /api/cards/*} 로 강제하므로, 이 필터가 다른 도메인 요청에 실행되는 일은 없다.
 */
public class CardRateLimitFilter extends OncePerRequestFilter {

    // 분당 허용 요청 수 — 임시값(검색/자동완성 debounce 감안 여유치), 팀 정책 확정 시 조정 필요
    private static final int CAPACITY_PER_MINUTE = 60;
    // bucketsByIp 무한 증가 방지용 유휴 만료 기준 — 이 시간 동안 요청이 없으면 정리 대상
    private static final Duration IDLE_TTL = Duration.ofMinutes(10);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);
    private static final String CLEANUP_THREAD_NAME = "card-rate-limit-cleanup";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final Map<String, BucketEntry> bucketsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, CLEANUP_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    private final MeterRegistry meterRegistry;

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    public CardRateLimitFilter() {
        this(new SimpleMeterRegistry());
    }

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    public CardRateLimitFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        cleanupExecutor.scheduleAtFixedRate(this::evictIdleEntries,
                CLEANUP_INTERVAL.toMillis(), CLEANUP_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        BucketEntry entry = bucketsByIp.computeIfAbsent(resolveClientIp(request), ip -> new BucketEntry(newBucket()));
        entry.touch();

        if (entry.bucket().tryConsume(1)) {
            // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
            meterRegistry.counter("card.ratelimit.allowed").increment();
            filterChain.doFilter(request, response);
            return;
        }

        // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
        meterRegistry.counter("card.ratelimit.rejected").increment();
        response.setStatus(ErrorCode.CARD_RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setContentType(JSON_CONTENT_TYPE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.CARD_RATE_LIMIT_EXCEEDED)));
    }

    @Override
    public void destroy() {
        cleanupExecutor.shutdownNow();
        super.destroy();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY_PER_MINUTE, Refill.intervally(CAPACITY_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank() && isTrustedProxy(remoteAddr)) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    /**
     * 이 프로젝트는 아직 리버스 프록시 배치 여부·신뢰 IP 대역이 팀 정책으로 확정되지
     * 않아, 임시로 loopback/사설 IP 대역에서 온 요청만 프록시로 신뢰한다. 운영 프록시
     * 대역이 확정되면 설정값 기반 검증으로 대체해야 한다.
     */
    private boolean isTrustedProxy(String remoteAddr) {
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private void evictIdleEntries() {
        evictIdleEntries(IDLE_TTL.toMillis());
    }

    private void evictIdleEntries(long idleThresholdMillis) {
        bucketsByIp.values().removeIf(entry -> entry.isIdleSince(idleThresholdMillis));
    }

    int bucketCountForTest() {
        return bucketsByIp.size();
    }

    void evictIdleEntriesForTest(long idleThresholdMillis) {
        evictIdleEntries(idleThresholdMillis);
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private final AtomicLong lastAccessAtMillis = new AtomicLong(System.currentTimeMillis());

        private BucketEntry(Bucket bucket) {
            this.bucket = bucket;
        }

        private Bucket bucket() {
            return bucket;
        }

        private void touch() {
            lastAccessAtMillis.set(System.currentTimeMillis());
        }

        private boolean isIdleSince(long thresholdMillis) {
            return System.currentTimeMillis() - lastAccessAtMillis.get() > thresholdMillis;
        }
    }
}
