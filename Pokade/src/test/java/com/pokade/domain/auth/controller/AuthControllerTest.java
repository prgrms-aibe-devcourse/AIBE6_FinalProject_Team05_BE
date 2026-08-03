package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.service.AuthService;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtAuthenticationFilter;
import com.pokade.global.security.JwtProperties;
import com.pokade.global.web.RefreshTokenCookieFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthControllerTest.TestConfig.class, RefreshTokenCookieFactory.class})
class AuthControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private AuthService authService;

    // SecurityConfig가 생성자에서 요구하는 빈들 — 슬라이스엔 없으므로 목으로 채움
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-key-at-least-32-bytes-0123456789",
                    Duration.ofMinutes(30),
                    Duration.ofDays(14)
            );
        }
    }

    @Test
    @DisplayName("유효한 로그인 요청이면 200과 함께 refresh 쿠키를 세팅하고 로그인 서비스를 호출한다")
    void login_ok() {
        given(authService.login(any())).willReturn(new TokenPair("access-token", "refresh-token"));

        MvcTestResult result = mockMvcTester.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@pokade.com\",\"password\":\"pokade1234\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refreshToken=refresh-token");
        then(authService).should().login(any());
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다")
    void login_invalidEmail() {
        mockMvcTester.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"pokade1234\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST);

        then(authService).should(never()).login(any());
    }

    @Test
    @DisplayName("유효한 refresh 쿠키로 재발급하면 200과 함께 새 refresh 쿠키를 세팅하고 access를 반환한다")
    void reissue_ok() {
        given(authService.reissue("old-refresh")).willReturn(new TokenPair("new-access", "new-refresh"));

        MvcTestResult result = mockMvcTester.post()
                .uri("/api/auth/reissue")
                .cookie(new Cookie("refreshToken", "old-refresh"))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refreshToken=new-refresh");
        then(authService).should().reissue("old-refresh");
    }

    @Test
    @DisplayName("로그아웃하면 200과 함께 refresh 쿠키를 만료(Max-Age=0)시키고 로그아웃 서비스를 호출한다")
    void logout_ok() {
        MvcTestResult result = mockMvcTester.post()
                .uri("/api/auth/logout")
                .cookie(new Cookie("refreshToken", "refresh-token"))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        then(authService).should().logout("refresh-token");
    }

    @Test
    @DisplayName("refresh 쿠키가 없어도 로그아웃은 200을 반환한다(멱등)")
    void logout_noCookie() {
        MvcTestResult result = mockMvcTester.post()
                .uri("/api/auth/logout")
                .exchange();

        assertThat(result).hasStatusOk();
        then(authService).should().logout(null);
    }

    @Test
    @DisplayName("grace 수렴 재발급이면 refresh 쿠키를 세팅하지 않고 새 access만 반환한다")
    void reissue_graceConverges_noCookie() {
        given(authService.reissue("old-refresh")).willReturn(new TokenPair("new-access", null));

        MvcTestResult result = mockMvcTester.post()
                .uri("/api/auth/reissue")
                .cookie(new Cookie("refreshToken", "old-refresh"))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull(); // 쿠키 미세팅
        then(authService).should().reissue("old-refresh");
    }
}
