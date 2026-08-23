package com.pokade.domain.price.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.dto.BuyOfferPaymentConfirmRequest;
import com.pokade.domain.price.dto.BuyOfferReadyRequest;
import com.pokade.domain.price.dto.BuyOfferReadyResponse;
import com.pokade.domain.price.dto.BuyOfferResponse;
import com.pokade.domain.price.service.PriceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BuyOfferController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class BuyOfferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PriceService priceService;

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

    private BuyOfferReadyRequest readyRequestOf(Long cardId, Integer price, ListingGrade grade) {
        return new BuyOfferReadyRequest(
                cardId, null, price, grade, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1");
    }

    @Test
    void 결제준비에_성공하면_200과_orderId_amount를_반환한다() throws Exception {
        BuyOfferReadyRequest request = readyRequestOf(1L, 250000, ListingGrade.S);
        BuyOfferReadyResponse response = new BuyOfferReadyResponse("bo-order-1", 253000);

        given(priceService.readyBuyOffer(anyLong(), any(BuyOfferReadyRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/buy-offers/ready")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("bo-order-1"))
                .andExpect(jsonPath("$.data.amount").value(253000));
    }

    @Test
    void 결제준비시_필수값이_없으면_400을_반환한다() throws Exception {
        BuyOfferReadyRequest invalidRequest =
                new BuyOfferReadyRequest(null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/buy-offers/ready")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 결제준비시_존재하지_않는_카드면_404를_반환한다() throws Exception {
        BuyOfferReadyRequest request = readyRequestOf(999L, 100000, null);

        given(priceService.readyBuyOffer(anyLong(), any(BuyOfferReadyRequest.class)))
                .willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(post("/api/buy-offers/ready")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void 결제승인에_성공하면_200과_등록된_입찰을_반환한다() throws Exception {
        BuyOfferPaymentConfirmRequest request = new BuyOfferPaymentConfirmRequest("pay_123", "bo-order-1", 253000L);
        BuyOfferResponse response = new BuyOfferResponse(
                5L, 1L, 100L, 10L, 250000, ListingGrade.S, "ACTIVE",
                "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1", LocalDateTime.now());

        given(priceService.confirmBuyOfferPurchase(100L, "pay_123", "bo-order-1", 253000L))
                .willReturn(response);

        mockMvc.perform(post("/api/buy-offers/confirm-payment")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5L))
                .andExpect(jsonPath("$.data.buyerId").value(100L))
                .andExpect(jsonPath("$.data.grade").value("S"));
    }

    @Test
    void 결제승인시_orderId가_없으면_400을_반환한다() throws Exception {
        BuyOfferPaymentConfirmRequest invalidRequest = new BuyOfferPaymentConfirmRequest("pay_123", "", 253000L);

        mockMvc.perform(post("/api/buy-offers/confirm-payment")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 포인트로_전액_결제해_paymentKey가_없어도_200을_반환한다() throws Exception {
        // paymentKey는 DTO 레벨에서는 필수가 아니다 - 결제 금액이 0보다 클 때만 서비스 레벨에서
        // 필수로 검증한다(PriceServiceTest에서 검증). 여기서는 서비스를 목으로 대체하므로
        // paymentKey 없이도 요청 자체는 컨트롤러를 통과하는지만 확인한다.
        BuyOfferPaymentConfirmRequest request = new BuyOfferPaymentConfirmRequest(null, "bo-order-1", 0L);
        BuyOfferResponse response = new BuyOfferResponse(
                5L, 1L, 100L, 10L, 250000, ListingGrade.S, "ACTIVE",
                "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1", LocalDateTime.now());

        given(priceService.confirmBuyOfferPurchase(100L, null, "bo-order-1", 0L)).willReturn(response);

        mockMvc.perform(post("/api/buy-offers/confirm-payment")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5L));
    }

    @Test
    void 결제승인시_이미_처리된_주문이면_409를_반환한다() throws Exception {
        BuyOfferPaymentConfirmRequest request = new BuyOfferPaymentConfirmRequest("pay_123", "bo-order-1", 253000L);

        given(priceService.confirmBuyOfferPurchase(100L, "pay_123", "bo-order-1", 253000L))
                .willThrow(new BusinessException(ErrorCode.BUY_OFFER_ORDER_ALREADY_PROCESSED));

        mockMvc.perform(post("/api/buy-offers/confirm-payment")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUY_OFFER_ORDER_ALREADY_PROCESSED"));
    }
}
