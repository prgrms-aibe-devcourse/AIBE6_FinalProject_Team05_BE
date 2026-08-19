package com.pokade.domain.notification.service;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.event.NotificationPushEvent;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.store.SseEmitterStore;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock SseEmitterStore sseEmitterStore;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks NotificationService notificationService;

    private Notification notification(Long userId) {
        return Notification.builder()
                .userId(userId).type(NotificationType.PRICE_TARGET).message("메시지")
                .build();
    }

    // ===== 목록 조회 =====
    @Test
    @DisplayName("목록 조회: 알림이 없으면 빈 페이지 반환")
    void getNotifications_empty() {
        Pageable pageable = PageRequest.of(0, 20);
        given(notificationRepository.findByUserId(1L, pageable)).willReturn(Page.empty(pageable));

        Page<NotificationResponse> result = notificationService.getNotifications(1L, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("목록 조회: 알림 개수만큼 NotificationResponse 페이지 반환")
    void getNotifications_success() {
        Pageable pageable = PageRequest.of(0, 20);
        given(notificationRepository.findByUserId(1L, pageable))
                .willReturn(new PageImpl<>(List.of(notification(1L), notification(1L)), pageable, 2));

        Page<NotificationResponse> result = notificationService.getNotifications(1L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ===== 읽음 처리 =====
    // markAsReadIfUnread()는 "조회 후 갱신"이 아니라 조건부 원자적 UPDATE라, 존재하지 않는 알림과
    // 이미 읽은 알림을 구분하려면 0건 갱신 시에만 findByIdAndUserId로 원인을 판별한다.
    @Test
    @DisplayName("읽음 처리: 원자적 갱신이 1건이면 정상 처리되고 존재 여부를 다시 조회하지 않는다")
    void markAsRead_success() {
        given(notificationRepository.markAsReadIfUnread(1L, 1L)).willReturn(1);

        notificationService.markAsRead(1L, 1L);

        then(notificationRepository).should(Mockito.times(1)).markAsReadIfUnread(1L, 1L);
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

    // ===== 삭제 =====
    // deleteByIdAndUserId()도 markAsReadIfUnread()와 같은 이유로 조건부 원자적 DELETE라, 존재하지 않는
    // 알림과 본인 소유가 아닌 알림을 구분하지 않고 0건이면 그대로 NOTIFICATION_NOT_FOUND로 처리한다.
    @Test
    @DisplayName("삭제: 원자적 삭제가 1건이면 정상 처리된다")
    void deleteNotification_success() {
        given(notificationRepository.deleteByIdAndUserId(1L, 1L)).willReturn(1);

        notificationService.deleteNotification(1L, 1L);

        then(notificationRepository).should(Mockito.times(1)).deleteByIdAndUserId(1L, 1L);
    }

    @Test
    @DisplayName("삭제: 삭제 0건이면(존재하지 않거나 본인 소유가 아님) NOTIFICATION_NOT_FOUND")
    void deleteNotification_notFound() {
        given(notificationRepository.deleteByIdAndUserId(1L, 1L)).willReturn(0);

        assertThatThrownBy(() -> notificationService.deleteNotification(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
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

    @Test
    @DisplayName("목표가 도달 알림 생성: 구독 중인 Emitter가 있으면 notification 이벤트를 전송한다")
    void createPriceTargetNotification_pushes_to_subscriber() throws Exception {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(100000).build();
        SseEmitter emitter = mock(SseEmitter.class);
        given(sseEmitterStore.findByUserId(1L)).willReturn(List.of(emitter));

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000);

        then(emitter).should().send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("목표가 도달 알림 생성: 구독 중인 Emitter가 없으면 예외 없이 조용히 스킵된다")
    void createPriceTargetNotification_no_subscriber_noop() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(100000).build();
        given(sseEmitterStore.findByUserId(1L)).willReturn(List.of());

        assertThatCode(() -> notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("목표가 도달 알림 생성: 전송 중 IOException이 발생하면 Emitter를 종료 처리한다")
    void createPriceTargetNotification_send_fails_completesWithError() throws Exception {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).targetBuyPrice(100000).build();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        given(sseEmitterStore.findByUserId(1L)).willReturn(List.of(emitter));

        notificationService.createPriceTargetNotification(watchlist, "리자몽", 100000);

        then(emitter).should().completeWithError(any(IOException.class));
    }

    // ===== 1:1 문의 처리 완료 알림 생성 =====
    @Test
    @DisplayName("문의 처리 완료 알림 생성: INQUIRY_HANDLED 타입으로 저장하고, 커밋 이후 푸시를 위한 이벤트를 발행한다")
    void createInquiryHandledNotification_savesAndPublishesEvent() {
        notificationService.createInquiryHandledNotification(1L, "결제 문의");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(NotificationType.INQUIRY_HANDLED);
        assertThat(saved.getMessage()).contains("결제 문의");

        ArgumentCaptor<NotificationPushEvent> eventCaptor = ArgumentCaptor.forClass(NotificationPushEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().response().type()).isEqualTo(NotificationType.INQUIRY_HANDLED);

        // 이벤트 발행 시점엔 아직 커밋 전이므로, 이 메서드 자체는 Emitter로 직접 전송하지 않는다.
        then(sseEmitterStore).should(never()).findByUserId(any());
    }

    // ===== 커밋 후 알림 푸시 (AFTER_COMMIT 리스너) =====
    @Test
    @DisplayName("onNotificationPush: 구독 중인 Emitter가 있으면 notification 이벤트를 전송한다")
    void onNotificationPush_pushes_to_subscriber() throws Exception {
        NotificationResponse response = new NotificationResponse(1L, NotificationType.INQUIRY_HANDLED, "메시지", false, null);
        SseEmitter emitter = mock(SseEmitter.class);
        given(sseEmitterStore.findByUserId(1L)).willReturn(List.of(emitter));

        notificationService.onNotificationPush(new NotificationPushEvent(1L, response));

        then(emitter).should().send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("onNotificationPush: 구독 중인 Emitter가 없으면 예외 없이 조용히 스킵된다")
    void onNotificationPush_no_subscriber_noop() {
        NotificationResponse response = new NotificationResponse(1L, NotificationType.INQUIRY_HANDLED, "메시지", false, null);
        given(sseEmitterStore.findByUserId(1L)).willReturn(List.of());

        assertThatCode(() -> notificationService.onNotificationPush(new NotificationPushEvent(1L, response)))
                .doesNotThrowAnyException();
    }

    // ===== SSE 구독 =====
    @Test
    @DisplayName("subscribe: Emitter를 생성해 저장소에 등록하고 반환한다")
    void subscribe_registers_emitter() {
        SseEmitter emitter = notificationService.subscribe(1L);

        assertThat(emitter).isNotNull();
        then(sseEmitterStore).should().save(eq(1L), eq(emitter));
    }

    // ===== 하트비트 =====
    @Test
    @DisplayName("sendHeartbeat: 등록된 모든 Emitter에 comment 이벤트를 보낸다")
    void sendHeartbeat_sends_to_every_emitter() throws Exception {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        given(sseEmitterStore.findAll()).willReturn(List.of(first, second));

        notificationService.sendHeartbeat();

        then(first).should().send(any(SseEmitter.SseEventBuilder.class));
        then(second).should().send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("sendHeartbeat: 하나의 Emitter 전송이 실패해도 나머지 Emitter는 정상 전송된다")
    void sendHeartbeat_one_failure_does_not_block_others() throws Exception {
        SseEmitter failing = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(failing).send(any(SseEmitter.SseEventBuilder.class));
        given(sseEmitterStore.findAll()).willReturn(List.of(failing, healthy));

        assertThatCode(() -> notificationService.sendHeartbeat()).doesNotThrowAnyException();

        then(failing).should().completeWithError(any(IOException.class));
        then(healthy).should().send(any(SseEmitter.SseEventBuilder.class));
        then(healthy).should(never()).completeWithError(any());
    }

    @Test
    @DisplayName("sendHeartbeat: 등록된 Emitter가 없으면 예외 없이 조용히 끝난다")
    void sendHeartbeat_no_subscribers_noop() {
        given(sseEmitterStore.findAll()).willReturn(List.of());

        assertThatCode(() -> notificationService.sendHeartbeat()).doesNotThrowAnyException();
    }
}
