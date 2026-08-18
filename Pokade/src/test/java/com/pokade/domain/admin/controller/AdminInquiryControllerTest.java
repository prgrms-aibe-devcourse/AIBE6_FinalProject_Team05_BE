package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.service.AdminInquiryService;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInquiryController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AdminInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInquiryService adminInquiryService;

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
    void 관리자가_문의_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        InquiryResponse response = new InquiryResponse(1L, 100L, "제목", "내용", LocalDateTime.now());
        given(adminInquiryService.getInquiries()).willReturn(List.of(response));

        mockMvc.perform(get("/api/admin/inquiries").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("제목"));
    }

    @Test
    void 관리자가_아니면_문의_목록_조회시_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자가_문의_상세를_조회하면_200을_반환한다() throws Exception {
        InquiryResponse response = new InquiryResponse(1L, 100L, "제목", "내용", LocalDateTime.now());
        given(adminInquiryService.getInquiry(1L)).willReturn(response);

        mockMvc.perform(get("/api/admin/inquiries/{id}", 1L).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("내용"));
    }

    @Test
    void 존재하지_않는_문의_상세_조회시_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.INQUIRY_NOT_FOUND))
                .given(adminInquiryService).getInquiry(999L);

        mockMvc.perform(get("/api/admin/inquiries/{id}", 999L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }
}
