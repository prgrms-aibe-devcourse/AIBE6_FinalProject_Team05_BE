package com.pokade.domain.admin.controller;

import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
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

@WebMvcTest(AdminTradeController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AdminTradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;

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

    private TradeResponse tradeResponseOf(Long id, TradeStatus status) {
        return new TradeResponse(
                id, 10L, 200L, 100L, 1L, "리자몽 ex", 10000, status,
                LocalDateTime.now(), null, null, null, null, LocalDateTime.now());
    }

    @Test
    void 검수_배송_대기_목록_조회에_성공하면_200을_반환한다() throws Exception {
        given(tradeService.getPendingTrades())
                .willReturn(List.of(tradeResponseOf(1L, TradeStatus.SHIPPED_TO_PLATFORM)));

        mockMvc.perform(get("/api/admin/trades").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SHIPPED_TO_PLATFORM"));
    }

    @Test
    void 관리자가_아니면_대기_목록_조회시_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/trades").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 검수_완료_처리에_성공하면_200을_반환한다() throws Exception {
        given(tradeService.markInspected(1L)).willReturn(tradeResponseOf(1L, TradeStatus.INSPECTED));

        mockMvc.perform(patch("/api/admin/trades/{id}/inspect", 1L).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INSPECTED"));
    }

    @Test
    void 관리자가_아니면_검수_완료_처리시_403을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/trades/{id}/inspect", 1L).with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_거래를_검수처리하면_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND))
                .given(tradeService).markInspected(999L);

        mockMvc.perform(patch("/api/admin/trades/{id}/inspect", 999L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 배송_완료_처리에_성공하면_200을_반환한다() throws Exception {
        given(tradeService.markDelivered(1L)).willReturn(tradeResponseOf(1L, TradeStatus.DELIVERED));

        mockMvc.perform(patch("/api/admin/trades/{id}/deliver", 1L).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void 관리자가_아니면_배송_완료_처리시_403을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/trades/{id}/deliver", 1L).with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_거래를_배송처리하면_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND))
                .given(tradeService).markDelivered(999L);

        mockMvc.perform(patch("/api/admin/trades/{id}/deliver", 999L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }
}
