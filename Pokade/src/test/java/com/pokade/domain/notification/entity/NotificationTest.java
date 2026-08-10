package com.pokade.domain.notification.entity;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private Notification notification() {
        return Notification.builder()
                .userId(1L).type(NotificationType.PRICE_TARGET).message("메시지")
                .build();
    }

    @Test
    @DisplayName("markAsRead: 처음 호출하면 isRead가 true로 바뀐다")
    void markAsRead_success() {
        Notification notification = notification();

        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("markAsRead: 이미 읽음 상태에서 다시 호출하면 NOTIFICATION_ALREADY_READ")
    void markAsRead_alreadyRead() {
        Notification notification = notification();
        notification.markAsRead();

        assertThatThrownBy(notification::markAsRead)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_ALREADY_READ);
    }
}
