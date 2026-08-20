package com.pokade.domain.point.controller;

import com.pokade.domain.point.dto.request.PointChargeReadyRequest;
import com.pokade.domain.point.dto.response.PointChargeReadyResponse;
import com.pokade.domain.point.service.PointChargeService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointChargeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class}) // 401 계약을 검증하려면 실물 엔트리포인트가 필요
class PointChargeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointChargeService pointChargeService;

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

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    void 로그인한_사용자가_충전을_준비하면_200과_주문정보를_반환한다() throws Exception {
        given(pointChargeService.ready(eq(100L), any(PointChargeReadyRequest.class)))
                .willReturn(new PointChargeReadyResponse("order-1", 10000));

        mockMvc.perform(post("/api/points/charge/ready")
                        .with(userId(100L))
                        .contentType("application/json")
                        .content("{\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order-1"))
                .andExpect(jsonPath("$.data.amount").value(10000));
    }

    @Test
    void 충전_금액이_최소금액_미만이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/points/charge/ready")
                        .with(userId(100L))
                        .contentType("application/json")
                        .content("{\"amount\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_충전_준비시_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/points/charge/ready")
                        .contentType("application/json")
                        .content("{\"amount\":10000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 결제_승인이_성공하면_200과_충전후_잔액을_반환한다() throws Exception {
        given(pointChargeService.confirm(100L, "pay-1", "order-1", 10000L)).willReturn(20000);

        mockMvc.perform(post("/api/points/charge/confirm")
                        .with(userId(100L))
                        .contentType("application/json")
                        .content("{\"paymentKey\":\"pay-1\",\"orderId\":\"order-1\",\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20000));
    }

    @Test
    void 결제_승인이_실패하면_402를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED))
                .given(pointChargeService).confirm(100L, "pay-1", "order-1", 10000L);

        mockMvc.perform(post("/api/points/charge/confirm")
                        .with(userId(100L))
                        .contentType("application/json")
                        .content("{\"paymentKey\":\"pay-1\",\"orderId\":\"order-1\",\"amount\":10000}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
    }
}
