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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void 목록_조회에_성공하면_200과_페이지를_반환한다() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, NotificationType.PRICE_TARGET, "메시지", false, LocalDateTime.now());

        given(notificationService.getNotifications(eq(100L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/notifications").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 알림이_없으면_빈_페이지와_200을_반환한다() throws Exception {
        given(notificationService.getNotifications(eq(100L), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/notifications").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 페이징_파라미터가_없으면_20건_createdAt_DESC가_기본값이다() throws Exception {
        given(notificationService.getNotifications(any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/notifications").with(userId(100L)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(100L), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
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
    void 삭제에_성공하면_200을_반환한다() throws Exception {
        willDoNothing().given(notificationService).deleteNotification(anyLong(), anyLong());

        mockMvc.perform(delete("/api/notifications/1").with(userId(100L)))
                .andExpect(status().isOk());

        then(notificationService).should().deleteNotification(100L, 1L);
    }

    @Test
    void 존재하지_않거나_본인_소유가_아닌_알림_삭제시_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).deleteNotification(anyLong(), anyLong());

        mockMvc.perform(delete("/api/notifications/999").with(userId(100L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
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
