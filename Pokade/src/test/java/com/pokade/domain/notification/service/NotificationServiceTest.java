package com.pokade.domain.notification.service;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    // markAsReadIfUnread()는 "조회 후 갱신"이 아니라 조건부 원자적 UPDATE라, 존재하지 않는 알림과
    // 이미 읽은 알림을 구분하려면 0건 갱신 시에만 findByIdAndUserId로 원인을 판별한다.
    @Test
    @DisplayName("읽음 처리: 원자적 갱신이 1건이면 정상 처리되고 존재 여부를 다시 조회하지 않는다")
    void markAsRead_success() {
        given(notificationRepository.markAsReadIfUnread(1L, 1L)).willReturn(1);

        notificationService.markAsRead(1L, 1L);

        then(notificationRepository).should(Mockito.never()).findByIdAndUserId(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("읽음 처리: 갱신 0건이고 존재하지도 않으면 NOTIFICATION_NOT_FOUND")
    void markAsRead_notFound() {
        given(notificationRepository.markAsReadIfUnread(1L, 1L)).willReturn(0);
        given(notificationRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("읽음 처리: 갱신 0건인데 존재는 하면(이미 읽음, 동시 요청 경합 포함) NOTIFICATION_ALREADY_READ")
    void markAsRead_alreadyRead() {
        given(notificationRepository.markAsReadIfUnread(1L, 1L)).willReturn(0);
        given(notificationRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(notification(1L)));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_ALREADY_READ);
    }

    // ===== 목표가 도달 알림 생성 =====
    @Test
    @DisplayName("목표가 도달 알림 생성: notificationRepository.save가 호출된다")
    void createPriceTargetNotification_saves() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(100000).build();

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000);

        then(notificationRepository).should().save(Mockito.any(Notification.class));
    }

    @Test
    @DisplayName("목표가 도달 알림 생성: PRICE_TARGET 타입과 워치리스트의 userId로 저장된다")
    void createPriceTargetNotification_type() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(100000).build();

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(NotificationType.PRICE_TARGET);
    }

    @Test
    @DisplayName("목표가 도달 알림 생성: 메시지에 카드명, 목표가, '판매' 라벨이 포함된다")
    void createPriceTargetNotification_message_sell() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetSellPrice(150000).build();

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 150000);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("리자몽")
                .contains("150,000")
                .contains("판매");
    }

    @Test
    @DisplayName("목표가 도달 알림 생성: 도달한 쪽이 구매 목표가면 메시지에 '구매' 라벨이 포함된다")
    void createPriceTargetNotification_message_buy() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L)
                .targetBuyPrice(100000).targetSellPrice(150000).build();

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("100,000")
                .contains("구매");
    }
}
