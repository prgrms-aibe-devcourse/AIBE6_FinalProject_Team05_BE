package com.pokade.domain.trade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.trade.dto.TradePaymentConfirmRequest;
import com.pokade.domain.trade.dto.TradeReadyRequest;
import com.pokade.domain.trade.dto.TradeReadyResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.global.security.oauth.RedisAuthorizationRequestRepository;
import com.pokade.global.security.oauth.CustomOAuth2UserService;
import com.pokade.global.security.oauth.OAuth2LoginFailureHandler;
import com.pokade.global.security.oauth.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    void 결제준비에_성공하면_200과_orderId_amount를_반환한다() throws Exception {
        TradeReadyRequest request = new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");
        TradeReadyResponse response = new TradeReadyResponse("order-1", 10000);

        given(tradeService.ready(anyLong(), any(TradeReadyRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/trades/ready")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order-1"))
                .andExpect(jsonPath("$.data.amount").value(10000));
    }

    @Test
    void 결제준비시_listingId가_없으면_400을_반환한다() throws Exception {
        TradeReadyRequest invalidRequest = new TradeReadyRequest(null, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");

        mockMvc.perform(post("/api/trades/ready")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 결제준비시_본인_매물을_구매하려하면_400을_반환한다() throws Exception {
        TradeReadyRequest request = new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");

        given(tradeService.ready(anyLong(), any(TradeReadyRequest.class)))
                .willThrow(new BusinessException(ErrorCode.SELF_PURCHASE_NOT_ALLOWED));

        mockMvc.perform(post("/api/trades/ready")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PURCHASE_NOT_ALLOWED"));
    }

    @Test
    void 결제준비시_존재하지_않는_매물이면_404를_반환한다() throws Exception {
        TradeReadyRequest request = new TradeReadyRequest(999L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");

        given(tradeService.ready(anyLong(), any(TradeReadyRequest.class)))
                .willThrow(new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        mockMvc.perform(post("/api/trades/ready")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    void 결제승인에_성공하면_200과_생성된_거래를_반환한다() throws Exception {
        TradePaymentConfirmRequest request = new TradePaymentConfirmRequest("pay_123", "order-1", 10000L);
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.PENDING,
                null, null, null, null, null, null, null, null, LocalDateTime.now(), null);

        given(tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000L))
                .willReturn(response);

        mockMvc.perform(post("/api/trades/confirm-payment")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.buyerId").value(200L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 결제승인시_orderId가_없으면_400을_반환한다() throws Exception {
        TradePaymentConfirmRequest invalidRequest = new TradePaymentConfirmRequest("pay_123", "", 10000L);

        mockMvc.perform(post("/api/trades/confirm-payment")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 포인트로_전액_결제해_paymentKey가_없어도_200을_반환한다() throws Exception {
        // paymentKey는 DTO 레벨에서는 필수가 아니다 - 결제 금액이 0보다 클 때만 서비스 레벨에서
        // 필수로 검증한다(TradeServiceTest에서 검증). 여기서는 서비스를 목으로 대체하므로
        // paymentKey 없이도 요청 자체는 컨트롤러를 통과하는지만 확인한다.
        TradePaymentConfirmRequest request = new TradePaymentConfirmRequest(null, "order-1", 0L);
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.PENDING,
                null, null, null, null, null, null, null, null, LocalDateTime.now(), null);

        given(tradeService.confirmPurchase(200L, null, "order-1", 0L)).willReturn(response);

        mockMvc.perform(post("/api/trades/confirm-payment")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void 결제는_승인됐지만_매물이_이미_팔렸으면_409를_반환한다() throws Exception {
        TradePaymentConfirmRequest request = new TradePaymentConfirmRequest("pay_123", "order-1", 10000L);

        given(tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000L))
                .willThrow(new BusinessException(ErrorCode.TRADE_CONFLICT));

        mockMvc.perform(post("/api/trades/confirm-payment")
                        .with(userId(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_CONFLICT"));
    }

    @Test
    void 본인_거래를_조회하면_200과_거래정보를_반환한다() throws Exception {
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.PENDING,
                null, null, null, null, null, null, null, null, LocalDateTime.now(), null);

        given(tradeService.getTrade(200L, 1L)).willReturn(response);

        mockMvc.perform(get("/api/trades/{id}", 1L)
                        .with(userId(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.buyerId").value(200L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 본인_거래가_아니면_403을_반환한다() throws Exception {
        given(tradeService.getTrade(999L, 1L))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/trades/{id}", 1L)
                        .with(userId(999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_거래를_조회하면_404를_반환한다() throws Exception {
        given(tradeService.getTrade(200L, 999L))
                .willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        mockMvc.perform(get("/api/trades/{id}", 999L)
                        .with(userId(200L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 구매자가_확정하면_200과_COMPLETED_상태를_반환한다() throws Exception {
        LocalDateTime shippedAt = LocalDateTime.now().minusDays(3);
        LocalDateTime inspectedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(1);
        LocalDateTime confirmedAt = LocalDateTime.now();
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.COMPLETED,
                shippedAt, inspectedAt, deliveredAt, confirmedAt, confirmedAt, null, null, null, confirmedAt, null);

        given(tradeService.confirmTrade(200L, 1L)).willReturn(response);

        mockMvc.perform(patch("/api/trades/{id}/confirm", 1L)
                        .with(userId(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.shippedAt").exists())
                .andExpect(jsonPath("$.data.inspectedAt").exists())
                .andExpect(jsonPath("$.data.deliveredAt").exists());
    }

    @Test
    void 구매자가_아니면_확정시_403을_반환한다() throws Exception {
        given(tradeService.confirmTrade(100L, 1L))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(patch("/api/trades/{id}/confirm", 1L)
                        .with(userId(100L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_거래를_확정하면_404를_반환한다() throws Exception {
        given(tradeService.confirmTrade(200L, 999L))
                .willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        mockMvc.perform(patch("/api/trades/{id}/confirm", 999L)
                        .with(userId(200L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 이미_완료된_거래를_확정하면_400을_반환한다() throws Exception {
        given(tradeService.confirmTrade(200L, 1L))
                .willThrow(new BusinessException(ErrorCode.INVALID_TRADE_STATUS));

        mockMvc.perform(patch("/api/trades/{id}/confirm", 1L)
                        .with(userId(200L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRADE_STATUS"));
    }

    @Test
    void 구매자가_취소하면_200과_CANCELLED_상태를_반환한다() throws Exception {
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.CANCELLED,
                null, null, null, null, null, null, null, null, LocalDateTime.now(), null);

        given(tradeService.cancelTrade(200L, 1L)).willReturn(response);

        mockMvc.perform(patch("/api/trades/{id}/cancel", 1L)
                        .with(userId(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void 판매자가_취소하면_200과_CANCELLED_상태를_반환한다() throws Exception {
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 100L, 1L, "테스트카드", null, null, null, 10000, TradeStatus.CANCELLED,
                null, null, null, null, null, null, null, null, LocalDateTime.now(), null);

        given(tradeService.cancelTrade(100L, 1L)).willReturn(response);

        mockMvc.perform(patch("/api/trades/{id}/cancel", 1L)
                        .with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void 본인_거래가_아니면_취소시_403을_반환한다() throws Exception {
        given(tradeService.cancelTrade(999L, 1L))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(patch("/api/trades/{id}/cancel", 1L)
                        .with(userId(999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_거래를_취소하면_404를_반환한다() throws Exception {
        given(tradeService.cancelTrade(200L, 999L))
                .willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        mockMvc.perform(patch("/api/trades/{id}/cancel", 999L)
                        .with(userId(200L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 이미_완료된_거래를_취소하면_400을_반환한다() throws Exception {
        given(tradeService.cancelTrade(200L, 1L))
                .willThrow(new BusinessException(ErrorCode.INVALID_TRADE_STATUS));

        mockMvc.perform(patch("/api/trades/{id}/cancel", 1L)
                        .with(userId(200L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRADE_STATUS"));
    }
}
