package com.pokade.domain.card.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CardRateLimitFilterTest {

    // #386: 분당 임계값을 테스트에 리터럴로 복사해 두면(예전엔 60이 9곳에 박혀 있었다) 프로덕션 상수를
    // 조정할 때마다 테스트가 조용히 어긋난다. 같은 패키지이므로 프로덕션 상수를 직접 참조한다.
    private static final int CAPACITY = CardRateLimitFilter.CAPACITY_PER_MINUTE;

    // #343: 프로덕션의 no-arg 생성자를 없애면서, 레지스트리는 테스트가 직접 넘긴다.
    // 지표 값을 확인할 필요가 없는 테스트는 이 헬퍼로 일회용 레지스트리를 받는다.
    private CardRateLimitFilter newFilter() {
        return new CardRateLimitFilter(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("t1 임계값 이내 요청은 정상적으로 다음 필터체인으로 전달된다")
    void t1() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
        }
        Mockito.verify(filterChain, Mockito.times(CAPACITY)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("t2 임계값을 초과한 요청은 429와 함께 차단된다")
    void t2() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0.2");
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest overLimitRequest = new MockHttpServletRequest("GET", "/api/cards");
        overLimitRequest.setRemoteAddr("127.0.0.2");
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();

        filter.doFilter(overLimitRequest, overLimitResponse, filterChain);

        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
        assertThat(overLimitResponse.getContentAsString()).contains("CARD_RATE_LIMIT_EXCEEDED");
        Mockito.verify(filterChain, Mockito.times(CAPACITY)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("t3 IP가 다르면 각각 별도로 카운트된다")
    void t3() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0.3");
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest otherIpRequest = new MockHttpServletRequest("GET", "/api/cards");
        otherIpRequest.setRemoteAddr("127.0.0.4");
        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();

        filter.doFilter(otherIpRequest, otherIpResponse, filterChain);

        assertThat(otherIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("t4 신뢰되지 않은 origin은 X-Forwarded-For를 조작해도 실제 remoteAddr 기준으로 카운트된다")
    void t4() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        String untrustedRemoteAddr = "203.0.113.10";

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr(untrustedRemoteAddr);
            request.addHeader("X-Forwarded-For", "1.1.1." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest overLimitRequest = new MockHttpServletRequest("GET", "/api/cards");
        overLimitRequest.setRemoteAddr(untrustedRemoteAddr);
        overLimitRequest.addHeader("X-Forwarded-For", "9.9.9.9");
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();

        filter.doFilter(overLimitRequest, overLimitResponse, filterChain);

        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("t5 신뢰된 프록시(loopback/사설 IP)에서는 X-Forwarded-For가 여전히 반영된다")
    void t5() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        String trustedRemoteAddr = "127.0.0.1";

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr(trustedRemoteAddr);
            request.addHeader("X-Forwarded-For", "10.1.2.3");
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest sameForwardedIpRequest = new MockHttpServletRequest("GET", "/api/cards");
        sameForwardedIpRequest.setRemoteAddr(trustedRemoteAddr);
        sameForwardedIpRequest.addHeader("X-Forwarded-For", "10.1.2.3");
        MockHttpServletResponse sameForwardedIpResponse = new MockHttpServletResponse();
        filter.doFilter(sameForwardedIpRequest, sameForwardedIpResponse, filterChain);

        assertThat(sameForwardedIpResponse.getStatus()).isEqualTo(429);

        MockHttpServletRequest otherForwardedIpRequest = new MockHttpServletRequest("GET", "/api/cards");
        otherForwardedIpRequest.setRemoteAddr(trustedRemoteAddr);
        otherForwardedIpRequest.addHeader("X-Forwarded-For", "10.1.2.4");
        MockHttpServletResponse otherForwardedIpResponse = new MockHttpServletResponse();
        filter.doFilter(otherForwardedIpRequest, otherForwardedIpResponse, filterChain);

        assertThat(otherForwardedIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("t6 유휴 시간이 지난 bucketsByIp 항목은 정리되어 무한정 쌓이지 않는다")
    void t6() throws Exception {
        CardRateLimitFilter filter = newFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0." + (10 + i));
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }
        assertThat(filter.bucketCountForTest()).isEqualTo(5);

        // Long.MIN_VALUE 임계값을 주면 "경과 시간 > 임계값"이 실제 경과 시간(0이어도)과 무관하게
        // 항상 참이 되어, 실제로 시간이 흐르길 기다리지 않고도 유휴 판정 로직을 결정론적으로 검증할 수 있다.
        filter.evictIdleEntriesForTest(Long.MIN_VALUE);

        assertThat(filter.bucketCountForTest()).isEqualTo(0);
    }

    @Test
    @DisplayName("t7 통과/차단 횟수가 각각 allowed·rejected 카운터에 기록된다")
    void t7() throws Exception {
        // 지표 값을 검증하는 유일한 테스트라 헬퍼 대신 레지스트리를 직접 들고 있는다.
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CardRateLimitFilter filter = new CardRateLimitFilter(meterRegistry);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < CAPACITY + 1; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0.7");
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        // 임계값을 딱 1회 넘긴 마지막 요청만 제한에 걸린다.
        assertThat(meterRegistry.counter("card.ratelimit.allowed").count()).isEqualTo((double) CAPACITY);
        assertThat(meterRegistry.counter("card.ratelimit.rejected").count()).isEqualTo(1.0);
    }
}
