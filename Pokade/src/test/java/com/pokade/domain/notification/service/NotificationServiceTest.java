package com.pokade.domain.notification.service;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationService notificationService;

    private Notification notification(Long userId) {
        return Notification.builder()
                .userId(userId).type(NotificationType.PRICE_TARGET).message("메시지")
                .build();
    }

    // ===== 목록 조회 =====
    @Test
    @DisplayName("목록 조회: 알림이 없으면 빈 리스트 반환")
    void getNotifications_empty() {
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).willReturn(List.of());

        List<NotificationResponse> result = notificationService.getNotifications(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("목록 조회: 알림 개수만큼 NotificationResponse 리스트 반환")
    void getNotifications_success() {
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(notification(1L), notification(1L)));

        List<NotificationResponse> result = notificationService.getNotifications(1L);

        assertThat(result).hasSize(2);
    }

    // ===== 읽음 처리 =====
    @Test
    @DisplayName("읽음 처리: 존재하지 않으면 NOTIFICATION_NOT_FOUND")
    void markAsRead_notFound() {
        given(notificationRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("읽음 처리: 정상 케이스면 notification.markAsRead()가 호출된다")
    void markAsRead_success() {
        Notification target = Mockito.spy(notification(1L));
        given(notificationRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        notificationService.markAsRead(1L, 1L);

        then(target).should().markAsRead();
        assertThat(target.isRead()).isTrue();
    }
}
