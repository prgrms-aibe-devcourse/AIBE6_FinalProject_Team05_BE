package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.domain.admin.metrics.dto.AdminMetricCardResponse;
import com.pokade.domain.admin.metrics.service.AdminMetricsService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.global.security.oauth.CustomOAuth2UserService;
import com.pokade.global.security.oauth.OAuth2LoginFailureHandler;
import com.pokade.global.security.oauth.OAuth2LoginSuccessHandler;
import com.pokade.global.security.oauth.RedisAuthorizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMetricsController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class}) // 401 계약을 검증하려면 실물 엔트리포인트가 필요
class AdminMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminMetricsService adminMetricsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @MockitoBean
    private RedisAuthorizationRequestRepository redisAuthorizationRequestRepository;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    private RequestPostProcessor admin() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return authentication(auth);
    }

    private RequestPostProcessor user() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    void 관리자가_대시보드를_조회하면_200과_카드_시리즈를_반환한다() throws Exception {
        AdminDashboardResponse response = new AdminDashboardResponse(
                List.of(new AdminMetricCardResponse("totalVisits", "총 방문자 수", 128540.0, "명", "오늘 증가", 42.0)),
                List.of());
        given(adminMetricsService.getDashboard()).willReturn(response);

        mockMvc.perform(get("/api/admin/metrics/dashboard").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].key").value("totalVisits"))
                .andExpect(jsonPath("$.data.cards[0].value").value(128540.0))
                .andExpect(jsonPath("$.data.cards[0].subLabel").value("오늘 증가"))
                .andExpect(jsonPath("$.data.cards[0].subValue").value(42.0));
    }

    @Test
    void 관리자가_아니면_대시보드_조회시_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/metrics/dashboard").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증되지_않았으면_대시보드_조회시_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/metrics/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
