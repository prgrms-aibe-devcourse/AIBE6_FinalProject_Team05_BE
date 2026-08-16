package com.pokade.domain.notification.controller;

import com.pokade.domain.auth.service.OAuth2LoginService;
import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.service.NotificationService;
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
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @MockitoBean
    private OAuth2LoginService oAuth2LoginService;

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
    void 목록_조회에_성공하면_200과_목록을_반환한다() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, NotificationType.PRICE_TARGET, "메시지", false, LocalDateTime.now());

        given(notificationService.getNotifications(100L)).willReturn(List.of(response));

        mockMvc.perform(get("/api/notifications").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    void 읽음_처리에_성공하면_200을_반환한다() throws Exception {
        willDoNothing().given(notificationService).markAsRead(anyLong(), anyLong());

        mockMvc.perform(patch("/api/notifications/1/read").with(userId(100L)))
                .andExpect(status().isOk());

        then(notificationService).should().markAsRead(100L, 1L);
    }

    @Test
    void 존재하지_않는_알림_읽음_처리시_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).markAsRead(anyLong(), anyLong());

        mockMvc.perform(patch("/api/notifications/999/read").with(userId(100L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 이미_읽은_알림_읽음_처리시_400을_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ))
                .given(notificationService).markAsRead(anyLong(), anyLong());

        mockMvc.perform(patch("/api/notifications/1/read").with(userId(100L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_ALREADY_READ"));
    }

    @Test
    void SSE_구독_요청은_비동기로_시작된다() throws Exception {
        given(notificationService.subscribe(100L)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/subscribe").with(userId(100L)))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());

        then(notificationService).should().subscribe(100L);
    }

    @Test
    void 인증되지_않은_SSE_구독_요청은_401을_반환한다() throws Exception {
        // 이 테스트 슬라이스의 jwtAuthenticationEntryPoint는 별도 스텁이 없으면 응답 상태를 건드리지 않아
        // 기본값(200)이 나온다. anyRequest().authenticated() 규칙이 실제로 이 경로를 막는지 검증하려면
        // 진입점이 401을 쓰도록 이 테스트에서만 동작을 지정한다.
        willAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).given(jwtAuthenticationEntryPoint).commence(any(), any(), any());

        mockMvc.perform(get("/api/notifications/subscribe"))
                .andExpect(status().isUnauthorized());

        then(notificationService).should(never()).subscribe(any());
    }
}
