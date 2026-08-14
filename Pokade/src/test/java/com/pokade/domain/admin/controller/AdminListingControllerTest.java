package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.service.AdminListingService;
import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.domain.report.entity.ReportStatus;
import com.pokade.domain.report.entity.ReportTargetType;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminListingController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AdminListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminListingService adminListingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

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
    void 신고_목록_조회에_성공하면_200을_반환한다() throws Exception {
        ReportResponse report = new ReportResponse(
                1L, ReportTargetType.LISTING, 10L, 100L, "허위 매물입니다", ReportStatus.PENDING, LocalDateTime.now());
        given(adminListingService.getListingReports()).willReturn(List.of(report));

        mockMvc.perform(get("/api/admin/reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].targetId").value(10L));
    }

    @Test
    void 관리자가_아니면_신고_목록_조회시_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/reports").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 매물_숨김_처리에_성공하면_200을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/listings/{id}/hide", 1L).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자가_아니면_매물_숨김_처리시_403을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/listings/{id}/hide", 1L).with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 이미_숨김_처리된_매물이면_400을_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_LISTING_STATUS))
                .given(adminListingService).hideListing(1L);

        mockMvc.perform(patch("/api/admin/listings/{id}/hide", 1L).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LISTING_STATUS"));
    }

    @Test
    void 존재하지_않는_매물이면_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.LISTING_NOT_FOUND))
                .given(adminListingService).hideListing(999L);

        mockMvc.perform(patch("/api/admin/listings/{id}/hide", 999L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }
}
