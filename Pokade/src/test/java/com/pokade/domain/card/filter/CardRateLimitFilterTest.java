package com.pokade.domain.card.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CardRateLimitFilterTest {

    @Test
    @DisplayName("t1 임계값 이내 요청은 정상적으로 다음 필터체인으로 전달된다")
    void t1() throws Exception {
        CardRateLimitFilter filter = new CardRateLimitFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
        }
        Mockito.verify(filterChain, Mockito.times(60)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("t2 임계값을 초과한 요청은 429와 함께 차단된다")
    void t2() throws Exception {
        CardRateLimitFilter filter = new CardRateLimitFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 60; i++) {
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
        Mockito.verify(filterChain, Mockito.times(60)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("t3 IP가 다르면 각각 별도로 카운트된다")
    void t3() throws Exception {
        CardRateLimitFilter filter = new CardRateLimitFilter();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 60; i++) {
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
}
