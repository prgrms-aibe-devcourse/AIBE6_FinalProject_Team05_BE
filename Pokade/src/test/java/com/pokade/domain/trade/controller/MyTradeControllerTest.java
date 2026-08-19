package com.pokade.domain.trade.controller;

import com.pokade.domain.trade.dto.MyTradeResponse;
import com.pokade.domain.trade.dto.MyTradeSearchCondition;
import com.pokade.domain.trade.dto.TradeRole;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.global.security.oauth.CustomOAuth2UserService;
import com.pokade.global.security.oauth.OAuth2LoginFailureHandler;
import com.pokade.global.security.oauth.OAuth2LoginSuccessHandler;
import com.pokade.global.security.oauth.RedisAuthorizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyTradeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class}) // 인가 계약을 검증하려면 실물 시큐리티 설정이 필요
class MyTradeControllerTest {

    private static final Long USER_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;                                // MyTradeController가 요구
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;                        // 실제 JwtAuthenticationFilter가 요구
    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;                  // JwtAuthenticationFilter가 요구
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;      // SecurityConfig가 요구
    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;      // SecurityConfig가 요구
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;          // SecurityConfig가 요구
    @MockitoBean
    private RedisAuthorizationRequestRepository authorizationRequestRepository; // SecurityConfig가 요구

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    private MyTradeResponse sampleResponse() {
        return new MyTradeResponse(
                10L, 20L, 30L, "리자몽", "https://img/small.png",
                50000, TradeStatus.COMPLETED, TradeRole.BUY, 99L,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 12, 9, 0));
    }

    @Test
    void 무토큰으로_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/me/trades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 조건에_맞는_거래가_없으면_빈목록과_200을_반환한다() throws Exception {
        given(tradeService.getMyTrades(eq(USER_ID), any(MyTradeSearchCondition.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/users/me/trades").with(userId(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void from이_to보다_늦으면_400_INVALID_PERIOD를_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/me/trades")
                        .param("from", "2026-05-20")
                        .param("to", "2026-05-10")
                        .with(userId(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void 요청_파라미터가_조회_조건으로_그대로_전달된다() throws Exception {
        given(tradeService.getMyTrades(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/users/me/trades")
                        .param("role", "SELL")
                        .param("status", "PENDING,COMPLETED")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31")
                        .with(userId(USER_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<MyTradeSearchCondition> captor = ArgumentCaptor.forClass(MyTradeSearchCondition.class);
        verify(tradeService).getMyTrades(eq(USER_ID), captor.capture(), any(Pageable.class));

        MyTradeSearchCondition condition = captor.getValue();
        assertThat(condition.role()).isEqualTo(TradeRole.SELL);
        assertThat(condition.statuses()).containsExactly(TradeStatus.PENDING, TradeStatus.COMPLETED);
        assertThat(condition.from()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(condition.to()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void 페이징_파라미터가_없으면_20건_createdAt_DESC가_기본값이다() throws Exception {
        given(tradeService.getMyTrades(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/users/me/trades").with(userId(USER_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tradeService).getMyTrades(eq(USER_ID), any(MyTradeSearchCondition.class), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 응답에_역할과_상대방_id가_담긴다() throws Exception {
        given(tradeService.getMyTrades(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/users/me/trades").with(userId(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].tradeId").value(10))
                .andExpect(jsonPath("$.data.content[0].role").value("BUY"))
                .andExpect(jsonPath("$.data.content[0].counterpartyId").value(99))
                .andExpect(jsonPath("$.data.content[0].cardName").value("리자몽"));
    }
}
