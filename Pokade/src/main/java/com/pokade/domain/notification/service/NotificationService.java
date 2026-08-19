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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    // FE가 만료 전에 재연결해야 하는 SSE 연결 유지 시간
    private static final long SSE_TIMEOUT_MILLIS = 30L * 60 * 1000;

    // 리버스 프록시(nginx/ALB 등)의 idle timeout이 보통 60초 안팎이라, 그보다 짧은 주기로 더미 코멘트를
    // 보내 연결이 유휴 상태로 조기 종료되지 않도록 한다.
    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;

    private final NotificationRepository notificationRepository;
    private final SseEmitterStore sseEmitterStore;
    private final ApplicationEventPublisher eventPublisher;

    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::of)
                .toList();
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        int updated = notificationRepository.markAsReadIfUnread(notificationId, userId);
        if (updated == 1) {
            return;
        }

        boolean exists = notificationRepository.findByIdAndUserId(notificationId, userId).isPresent();
        if (!exists) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ);
    }

    // 워치리스트 목표가 도달 알림 생성 (reachedTargetPrice: 도달한 것으로 판정된 목표가 - 구매/판매 목표가 중 실제로 도달한 쪽)
    @Transactional
    public void createPriceTargetNotification(Watchlist watchlist, String cardName, Integer reachedTargetPrice) {
        Notification notification = Notification.builder()
                .userId(watchlist.getUserId())
                .type(NotificationType.PRICE_TARGET)
                .message(buildPriceTargetMessage(watchlist, cardName, reachedTargetPrice))
                .build();

        notificationRepository.save(notification);
        pushToSubscribers(watchlist.getUserId(), NotificationResponse.of(notification));
    }

    private String buildPriceTargetMessage(Watchlist watchlist, String cardName, Integer reachedTargetPrice) {
        String targetLabel = reachedTargetPrice.equals(watchlist.getTargetBuyPrice()) ? "구매" : "판매";
        return String.format("%s 카드가 %s 목표가 %,d원에 도달했습니다.", cardName, targetLabel, reachedTargetPrice);
    }

    // 1:1 문의 처리 완료 알림 생성 - 관리자의 답변 등록, 또는 상태를 HANDLED로 변경한 경우 호출된다.
    // 알림 저장은 호출자 트랜잭션에 그대로 포함시키되, SSE 푸시는 커밋이 실제로 성공한 뒤에만 보낸다 -
    // 커밋 전에 푸시하면 이후 같은 트랜잭션 안에서 다른 작업이 실패해 롤백되는 경우, 유저는 이미 알림을
    // 받았는데 문의 상태/알림 레코드는 존재하지 않는 불일치가 생긴다.
    @Transactional
    public void createInquiryHandledNotification(Long userId, String inquiryTitle) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.INQUIRY_HANDLED)
                .message(String.format("'%s' 문의가 처리 완료되었습니다.", inquiryTitle))
                .build();

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationPushEvent(userId, NotificationResponse.of(notification)));
    }

    // 트랜잭션이 없는 컨텍스트(단위 테스트 등)에서도 그대로 실행되도록 fallbackExecution=true.
    // AFTER_COMMIT 리스너는 이미 커밋되어 끝난 트랜잭션 밖에서 호출되므로, 클래스 레벨 @Transactional을
    // 그대로 상속하면 Spring이 기동 시점에 예외를 던진다 - NOT_SUPPORTED로 명시적으로 트랜잭션을 배제한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationPush(NotificationPushEvent event) {
        pushToSubscribers(event.userId(), event.response());
    }

    // 로그인 유저의 SSE 구독을 등록한다. 인증은 기존 JwtAuthenticationFilter가 처리하므로
    // 여기서는 이미 인증된 userId를 받아 Emitter를 저장소에 등록하는 역할만 한다.
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        sseEmitterStore.save(userId, emitter);

        emitter.onCompletion(() -> sseEmitterStore.remove(userId, emitter));
        emitter.onTimeout(() -> sseEmitterStore.remove(userId, emitter));
        emitter.onError(e -> sseEmitterStore.remove(userId, emitter));

        // 연결 직후 더미 이벤트를 보내 프록시/브라우저가 연결을 바로 끊지 않도록 한다.
        sendEvent(emitter, SseEmitter.event().name("connect").data("connected"));

        return emitter;
    }

    // 구독 중인 Emitter가 없으면(구독 중이 아니면) 조용히 스킵한다.
    private void pushToSubscribers(Long userId, NotificationResponse response) {
        for (SseEmitter emitter : sseEmitterStore.findByUserId(userId)) {
            sendEvent(emitter, SseEmitter.event().name("notification").data(response));
        }
    }

    // 유휴 상태로 프록시에 의해 연결이 끊기지 않도록 데이터 없는 코멘트만 주기적으로 보낸다.
    // 이 이벤트는 FE의 이벤트 리스너(onmessage 등)에 아무런 영향을 주지 않는다.
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    public void sendHeartbeat() {
        for (SseEmitter emitter : sseEmitterStore.findAll()) {
            sendEvent(emitter, SseEmitter.event().comment("heartbeat"));
        }
    }

    // 하나의 Emitter 전송 실패가 나머지 Emitter 처리를 막지 않도록 예외를 여기서 흡수한다.
    private void sendEvent(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            log.info("SSE 전송 실패로 연결을 종료합니다.", e);
            emitter.completeWithError(e);
        }
    }
}
