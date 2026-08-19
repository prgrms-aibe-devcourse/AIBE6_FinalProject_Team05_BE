package com.pokade.domain.notification.service;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님.
    // required = false: 슬라이스 테스트엔 MeterRegistry 빈이 없어 컨텍스트 로딩이 깨지는 문제(#224 유사)를 막기 위함.
    @Autowired(required = false)
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
            // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님. 하트비트 전송 실패율만 별도로 보기 위해
            // 실패 카운터 지표명을 넘기는 오버로드를 쓴다 - 일반 알림 push(pushToSubscribers)는 대상 아님.
            sendEvent(emitter, SseEmitter.event().comment("heartbeat"), "notification.sse.heartbeat.failure.calls");
        }
    }

    // 하나의 Emitter 전송 실패가 나머지 Emitter 처리를 막지 않도록 예외를 여기서 흡수한다.
    private void sendEvent(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        sendEvent(emitter, event, null);
    }

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님. failureMetricName이 있을 때만 실패 카운터를 증가시킨다.
    private void sendEvent(SseEmitter emitter, SseEmitter.SseEventBuilder event, String failureMetricName) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            log.info("SSE 전송 실패로 연결을 종료합니다.", e);
            if (failureMetricName != null) {
                meterRegistry.counter(failureMetricName).increment();
            }
            emitter.completeWithError(e);
        }
    }
}
