package com.pokade.domain.notification.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.event.NotificationPushEvent;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.web.PageableValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // #162: 다른 페이징 API(AiGradeService/CardQueryService/ChatService)와 동일한 상한
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final CardRepository cardRepository;
    private final SseEmitterStore sseEmitterStore;
    private final ApplicationEventPublisher eventPublisher;

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님.
    // required = false: 슬라이스 테스트엔 MeterRegistry 빈이 없어 컨텍스트 로딩이 깨지는 문제(#224 유사)를 막기 위함.
    @Autowired(required = false)
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        Page<Notification> notifications = notificationRepository.findByUserId(userId, pageable);

        // cardId별로 각각 조회하면 페이지당(최대 MAX_PAGE_SIZE건) N+1이 되므로, distinct cardId로 한 번에
        // 배치 조회한다(WatchlistService.getWatchlist()와 동일한 패턴). cardId가 없는 알림(INQUIRY_HANDLED 등)은
        // 애초에 이 목록에서 제외되므로 cardById.get(null)이 자연스럽게 null을 반환한다.
        List<Long> cardIds = notifications.stream()
                .map(Notification::getCardId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // Map.of()는 null 키 조회 시 NPE를 던지므로(cardId가 없는 알림에서 cardById.get(null) 호출 시 문제),
        // 빈 맵 폴백도 null 키 조회가 안전한 Collections.emptyMap()을 쓴다.
        Map<Long, Card> cardById = cardIds.isEmpty()
                ? Collections.emptyMap()
                : cardRepository.findAllById(cardIds).stream()
                        .collect(Collectors.toMap(Card::getId, Function.identity()));

        return notifications.map(notification -> NotificationResponse.of(notification, cardById.get(notification.getCardId())));
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

    // #162: 본인 소유 알림만 삭제 가능 - 조건부 원자적 DELETE(WHERE id=:id AND userId=:userId)가 0건이면
    // 존재하지 않거나 본인 소유가 아닌 것이므로 구분 없이 NOTIFICATION_NOT_FOUND로 처리한다.
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    // 워치리스트 목표가 도달 알림 생성 (reachedTargetPrice: 도달한 것으로 판정된 목표가 - 구매/판매 목표가 중 실제로 도달한 쪽)
    // card: 호출자(WatchlistTargetPriceEvaluator/WatchlistTargetPriceNoticeProcessor)가 목표가 판정을 위해
    // 이미 조회해 둔 카드 - 여기서 다시 조회하지 않고 그대로 재사용해 SSE 즉시 푸시에도 카드 이미지를 채운다.
    @Transactional
    public void createPriceTargetNotification(Watchlist watchlist, String cardName, Card card, Integer reachedTargetPrice) {
        Notification notification = Notification.builder()
                .userId(watchlist.getUserId())
                .type(NotificationType.PRICE_TARGET)
                .message(buildPriceTargetMessage(watchlist, cardName, reachedTargetPrice))
                .cardId(watchlist.getCardId())
                .build();

        notificationRepository.save(notification);
        pushToSubscribers(watchlist.getUserId(), NotificationResponse.of(notification, card));
    }

    private String buildPriceTargetMessage(Watchlist watchlist, String cardName, Integer reachedTargetPrice) {
        String targetLabel = reachedTargetPrice.equals(watchlist.getTargetBuyPrice()) ? "구매" : "판매";
        return String.format("%s 카드가 %s 목표가 %,d원에 도달했습니다.", cardName, targetLabel, reachedTargetPrice);
    }

    // #300: 워치리스트에 등록한 카드에 매물이 없다가 새로 등록됐을 때(재입고) 알림 생성.
    // card: 호출자(WatchlistListingAvailableNoticeListener)가 이미 조회해 둔 카드 - createPriceTargetNotification과
    // 동일하게 여기서 다시 조회하지 않고 재사용해 SSE 즉시 푸시에도 카드 이미지를 채운다.
    @Transactional
    public void createListingAvailableNotification(Watchlist watchlist, String cardName, Card card) {
        Notification notification = Notification.builder()
                .userId(watchlist.getUserId())
                .type(NotificationType.LISTING_AVAILABLE)
                .message(String.format("%s 카드에 매물이 새로 등록됐어요. 지금 확인해보세요!", cardName))
                .cardId(watchlist.getCardId())
                .build();

        notificationRepository.save(notification);
        pushToSubscribers(watchlist.getUserId(), NotificationResponse.of(notification, card));
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
        eventPublisher.publishEvent(new NotificationPushEvent(userId, NotificationResponse.of(notification, null)));
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
