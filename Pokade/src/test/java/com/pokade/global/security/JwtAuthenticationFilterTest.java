package com.pokade.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "pokade-local-dev-jwt-secret-key-change-in-prod-0123456789";

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보를 세팅한다")
    void doFilterInternal_setsAuthenticationForValidToken() throws Exception {
        String token = jwtTokenProvider.createAccessToken(42L, "USER");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 통과한다")
    void doFilterInternal_skipsAuthenticationWhenNoHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 헤더면 인증 없이 통과한다")
    void doFilterInternal_skipsAuthenticationWhenNotBearer() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Basic abc123");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 인증 없이 통과한다")
    void doFilterInternal_skipsAuthenticationForInvalidToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer not-a-real-token");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
