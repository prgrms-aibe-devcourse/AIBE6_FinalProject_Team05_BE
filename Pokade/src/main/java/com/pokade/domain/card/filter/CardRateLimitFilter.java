package com.pokade.domain.card.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.exception.ErrorResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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

    private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = bucketsByIp.computeIfAbsent(resolveClientIp(request), ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(ErrorCode.CARD_RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.CARD_RATE_LIMIT_EXCEEDED)));
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY_PER_MINUTE, Refill.intervally(CAPACITY_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
