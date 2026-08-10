package com.pokade.domain.notification.controller;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
}
