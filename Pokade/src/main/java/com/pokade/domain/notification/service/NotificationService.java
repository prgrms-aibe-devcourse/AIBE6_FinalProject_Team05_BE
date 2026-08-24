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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 운영 계측 - #258 도입, 워치리스트/알림 대시보드가 사용 중. SSE 전송 실패를 경로별로 나눠 센다.
    // 하트비트 실패는 "연결이 이미 끊겼다"는 신호에 가깝지만, 알림 push 실패는 사용자가 받았어야 할
    // 알림이 실제로 유실됐다는 뜻이라 성격이 다르다 - 한 지표로 합치면 이 구분이 사라진다.
    private static final String HEARTBEAT_FAILURE_METRIC = "notification.sse.heartbeat.failure.calls";
    private static final String PUSH_FAILURE_METRIC = "notification.sse.push.failure.calls";

    private final NotificationRepository notificationRepository;
    private final CardRepository cardRepository;
    private final SseEmitterStore sseEmitterStore;
    private final ApplicationEventPublisher eventPublisher;

    // 계측 주입 규칙은 support/TestMetricsConfig javadoc 참조(#343).
    private final MeterRegistry meterRegistry;

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
    // 이미 조회해 둔 카드 - 여기서 다시 조회하지 않고 그대로 재사용해 푸시 payload의 카드 이미지를 채운다.
    //
    // #392: 예전에는 여기서 pushToSubscribers()를 직접 불러 커밋 전에 SSE를 쏘았다. 아래
    // createInquiryHandledNotification 주석이 설명하는 바로 그 문제(유저는 알림을 받았는데 이후 롤백돼
    // 레코드는 없는 불일치)를 이 경로만 그대로 안고 있었다 - 나머지 타입과 같이 AFTER_COMMIT 이벤트로 통일했다.
    @Transactional
    public void createPriceTargetNotification(Watchlist watchlist, String cardName, Card card, Integer reachedTargetPrice) {
        saveAndPublish(watchlist.getUserId(), NotificationType.PRICE_TARGET, watchlist.getCardId(),
                buildPriceTargetMessage(watchlist, cardName, reachedTargetPrice), card);
    }

    private String buildPriceTargetMessage(Watchlist watchlist, String cardName, Integer reachedTargetPrice) {
        String targetLabel = reachedTargetPrice.equals(watchlist.getTargetBuyPrice()) ? "구매" : "판매";
        return String.format("%s 카드가 %s 목표가 %,d원에 도달했습니다.", cardName, targetLabel, reachedTargetPrice);
    }

    // #300: 워치리스트에 등록한 카드에 매물이 없다가 새로 등록됐을 때(재입고) 알림 생성.
    // card: 호출자(WatchlistListingAvailableNoticeListener)가 이미 조회해 둔 카드 - createPriceTargetNotification과
    // 동일하게 여기서 다시 조회하지 않고 재사용해 푸시 payload의 카드 이미지를 채운다.
    // #392: 위와 같은 이유로 커밋 전 직접 푸시에서 AFTER_COMMIT 이벤트로 통일했다.
    @Transactional
    public void createListingAvailableNotification(Watchlist watchlist, String cardName, Card card) {
        saveAndPublish(watchlist.getUserId(), NotificationType.LISTING_AVAILABLE, watchlist.getCardId(),
                String.format("%s 카드에 상품이 새로 등록됐어요. 지금 확인해보세요!", cardName), card);
    }

    // 1:1 문의 처리 완료 알림 생성 - 관리자의 답변 등록, 또는 상태를 HANDLED로 변경한 경우 호출된다.
    // 알림 저장은 호출자 트랜잭션에 그대로 포함시키되, SSE 푸시는 커밋이 실제로 성공한 뒤에만 보낸다 -
    // 커밋 전에 푸시하면 이후 같은 트랜잭션 안에서 다른 작업이 실패해 롤백되는 경우, 유저는 이미 알림을
    // 받았는데 문의 상태/알림 레코드는 존재하지 않는 불일치가 생긴다.
    @Transactional
    public void createInquiryHandledNotification(Long userId, Long inquiryId, String inquiryTitle) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.INQUIRY_HANDLED)
                .message(String.format("'%s' 문의가 처리 완료되었습니다.", inquiryTitle))
                .inquiryId(inquiryId)
                .build();

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationPushEvent(userId, NotificationResponse.of(notification, null)));
    }

    // #392: 구매자가 구매확정을 눌러 에스크로가 해제되고 판매대금이 판매자에게 정산된 시점에, 판매자에게
    // 그 사실을 알린다. 판매자 입장에서는 자기가 하지 않은 액션(구매자의 확정)으로 잔액이 늘어나는
    // 지점이라 앱 안에 알림이 없으면 정산 사실을 알 방법이 없었다.
    //
    // card를 조회하지 않고 null로 넘기는 이유: 호출부(TradeService.confirmTrade)는 cardId만 알고 Card
    // 엔티티는 갖고 있지 않은데, 썸네일 하나 때문에 거래 확정 경로에 조회를 한 번 더 넣지 않는다.
    // cardId는 레코드에 저장되므로 목록 조회 시 getNotifications()의 배치 조회가 이미지까지 채워준다
    // (createInquiryHandledNotification과 동일한 선택).
    @Transactional
    public void createTradeConfirmedNotification(Long sellerId, Long cardId, String cardName, Integer settledAmount) {
        Notification notification = Notification.builder()
                .userId(sellerId)
                .type(NotificationType.TRADE_CONFIRMED)
                .message(String.format("%s 거래가 완료되어 %,d원이 정산되었습니다.", cardName, settledAmount))
                .cardId(cardId)
                .build();

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationPushEvent(sellerId, NotificationResponse.of(notification, null)));
    }

    // #392: 30일간 팔리지 않은 매물을 판매자에게 알린다. 기존에도 ListingStaleNoticeService가 같은 내용을
    // 이메일로 보내고 있었지만, 인앱 알림이 없어 앱 안에서는 이 사실이 어디에도 드러나지 않았다.
    // 이메일 발송은 그대로 두고 인앱 알림을 나란히 추가하는 것이라, 둘 중 하나가 실패해도 다른 하나는
    // 나가야 한다(호출부에서 try 범위를 분리해 처리).
    //
    // 문구를 카드명이 아니라 매물 번호로 쓰는 이유: 같은 내용을 보내는 기존 이메일
    // (ListingStaleNoticeService.notify)이 "매물 #{id}" 형식이라 둘을 맞춰 두 채널이 갈라지지 않게 한다.
    // 어느 카드인지는 cardId를 함께 저장해 목록의 카드 썸네일과 링크(/cards/{id})가 알려준다 -
    // 그래서 이 메서드는 카드명 조회를 요구하지 않고, 호출부에 CardRepository 의존성이 생기지 않는다.
    @Transactional
    public void createListingStaleNotification(Long sellerId, Long cardId, Long listingId) {
        Notification notification = Notification.builder()
                .userId(sellerId)
                .type(NotificationType.LISTING_STALE)
                .message(String.format("매물 #%d가 30일간 판매되지 않았습니다. 가격을 확인해보세요.", listingId))
                .cardId(cardId)
                .build();

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationPushEvent(sellerId, NotificationResponse.of(notification, null)));
    }

    /**
     * #392: 사용자가 1:1 문의를 등록했을 때 활성 관리자 전원에게 알린다(팬아웃).
     *
     * <p>수신자가 여러 명이라 지금까지의 단건 생성 메서드들과 구조가 다르다:
     * <ul>
     *   <li>저장은 {@code saveAll()}로 묶어 관리자 수와 무관하게 INSERT 왕복을 1회로 만든다.</li>
     *   <li>SSE 푸시는 {@link NotificationPushEvent}가 userId 단건 기준이라 관리자 수만큼 이벤트를
     *       발행한다. AFTER_COMMIT 리스너는 요청 스레드에서 동기 실행되므로, 관리자가 두 자릿수로
     *       늘고 그중 느린 연결이 있으면 문의 등록 API 응답이 그만큼 지연된다 - 그때는 푸시를
     *       비동기로 돌리는 별도 작업이 필요하다.</li>
     * </ul>
     *
     * <p>관리자가 한 명도 없으면(조회 결과가 빈 목록) 조용히 아무것도 하지 않는다 - 관리자 부재로
     * 사용자의 문의 등록 자체가 실패하면 안 되기 때문이다.
     *
     * @param adminIds 활성(ACTIVE) 관리자 id 목록 - 호출부가 조회해서 넘긴다
     */
    @Transactional
    public void createInquiryReceivedNotification(List<Long> adminIds, Long inquiryId, String inquiryTitle) {
        if (adminIds == null || adminIds.isEmpty()) {
            return;
        }

        String message = String.format("새 문의가 등록되었습니다: '%s'", inquiryTitle);
        List<Notification> notifications = adminIds.stream()
                .map(adminId -> Notification.builder()
                        .userId(adminId)
                        .type(NotificationType.INQUIRY_RECEIVED)
                        .message(message)
                        .inquiryId(inquiryId)
                        .build())
                .toList();

        // saveAll()이 돌려주는 인스턴스를 그대로 쓴다 - id가 채워진 건 이쪽이라, 인자로 넘긴 목록으로
        // 응답 DTO를 만들면 id가 null인 알림이 SSE로 나간다.
        for (Notification saved : notificationRepository.saveAll(notifications)) {
            eventPublisher.publishEvent(
                    new NotificationPushEvent(saved.getUserId(), NotificationResponse.of(saved, null)));
        }
    }

    // #392: 결제가 끝나 판매자가 상품을 발송해야 하는 시점. 즉시구매(confirmPurchase)와 즉시판매
    // (createMatchedTrade) 양쪽에서 같은 문구로 나간다 - 판매자 입장에서는 어느 경로로 팔렸든
    // "발송해야 한다"는 할 일이 동일하기 때문이다.
    @Transactional
    public void createTradeShippingRequiredNotification(Long sellerId, Long cardId, String cardName) {
        saveAndPublish(sellerId, NotificationType.TRADE_SHIPPING_REQUIRED, cardId,
                String.format("%s 카드가 판매되었습니다. 상품을 플랫폼으로 발송해 주세요.", cardName));
    }

    // #392: 검수를 마친 상품이 구매자에게 배송 완료된 시점. 구매자가 구매확정을 눌러야 판매자 정산이
    // 이뤄지므로(TradeService.confirmTrade), 확정을 잊으면 거래가 그 상태로 멈춘다.
    @Transactional
    public void createTradeDeliveredNotification(Long buyerId, Long cardId, String cardName) {
        saveAndPublish(buyerId, NotificationType.TRADE_DELIVERED, cardId,
                String.format("%s 카드가 배송 완료되었습니다. 확인 후 구매확정을 해주세요.", cardName));
    }

    /**
     * #392: 거래 취소 알림 - 취소를 누른 당사자가 아니라 <b>상대방</b>에게만 보낸다(누른 사람은 방금
     * 자기가 한 일이라 알림이 소음이다).
     *
     * <p>같은 사건이라도 받는 사람의 역할에 따라 알아야 할 내용이 다르다: 구매자는 돈이 돌아온다는 것을,
     * 판매자는 매물이 다시 판매 중이 됐다는 것을 알아야 한다. 그래서 문구를 역할별로 가른다.
     *
     * <p>구매자 문구를 "환불되었습니다"가 아니라 "환불됩니다"로 두는 이유: 토스 취소는 실제 환급까지
     * 시차가 있고 포인트 전액 결제는 토스 호출 자체가 없다 - 완료형으로 단정하면 잔액을 확인한
     * 사용자와 어긋난다.
     *
     * @param recipientIsBuyer 수신자가 구매자면 true, 판매자면 false
     */
    @Transactional
    public void createTradeCancelledNotification(Long recipientId, Long cardId, String cardName, boolean recipientIsBuyer) {
        String message = recipientIsBuyer
                ? String.format("%s 카드 거래가 취소되어 결제 금액이 환불됩니다.", cardName)
                : String.format("%s 카드 거래가 취소되었습니다. 매물이 다시 판매 중으로 돌아갔습니다.", cardName);
        saveAndPublish(recipientId, NotificationType.TRADE_CANCELLED, cardId, message);
    }

    // #392: 등록해 둔 구매 입찰이 판매자의 즉시판매로 체결된 시점. 입찰자는 아무 행동도 하지 않았는데
    // 거래가 시작되므로, 같은 트리거(createMatchedTrade)에서 판매자에게 가는 발송 요청 알림과 짝을 이룬다.
    @Transactional
    public void createBuyOfferMatchedNotification(Long bidderId, Long cardId, String cardName, Integer matchedPrice) {
        saveAndPublish(bidderId, NotificationType.BUY_OFFER_MATCHED, cardId,
                String.format("%s 카드 구매 입찰이 %,d원에 체결되었습니다.", cardName, matchedPrice));
    }

    /**
     * 구매 입찰이 새로 등록됐을 때 그 카드에 매물을 올려둔 판매자들에게 보내는 알림. 수신자가 여럿이라
     * {@link #createInquiryReceivedNotification}과 같은 팬아웃 구조를 쓴다 - 저장은 saveAll 한 번,
     * 푸시 이벤트만 인원수만큼 발행한다.
     *
     * <p>수신자 선별(중복 제거, 입찰자 본인 제외, 정지/탈퇴 판매자 제외)은 호출자인
     * BuyOfferReceivedNoticeListener가 이미 끝낸 상태로 넘긴다 - 여기서는 목록을 그대로 믿는다.
     *
     * <p>문의 팬아웃과 다른 점 두 가지: inquiryId 대신 cardId를 채워 알림을 누르면 카드 상세로
     * 가게 하고(notifications에는 buy_offer_id 컬럼이 없어 특정 입찰로 보낼 방법이 아직 없다),
     * 호출자가 문구를 만들려고 이미 조회해 둔 {@link Card}를 받아 푸시 payload의 썸네일까지 채운다.
     *
     * @param sellerIds 알림을 받을 판매자 ID - 비어 있으면 아무것도 하지 않는다(매물이 전부 입찰자
     *                  본인 것이었거나 애초에 없는 경우)
     * @param offeredPrice 입찰가(상품가) - 배송비/포인트를 뺀 값이라 판매자가 받는 정산 기준가와 같다
     */
    // 이 메서드만 REQUIRES_NEW인 이유: 유일한 호출 맥락이 AFTER_COMMIT 리스너
    // (BuyOfferReceivedNoticeListener)이기 때문이다. AFTER_COMMIT 콜백은 커밋이 끝난 뒤이긴 해도 아직
    // 트랜잭션 정리(cleanupAfterCompletion) 전이라 EntityManagerHolder가 스레드에 그대로 바인딩돼 있고
    // transactionActive도 true다. 그 상태에서 REQUIRED로 들어오면 새 트랜잭션이 열리는 게 아니라 이미
    // 커밋된 그 트랜잭션에 "참여"만 하게 되는데, 참여 트랜잭션은 isNewTransaction()이 false라
    // AbstractPlatformTransactionManager가 doCommit을 건너뛴다 - 즉 아래 saveAll()이 영영 커밋되지 않고
    // 커넥션 반납 시점에 사라진다. REQUIRES_NEW로 기존 리소스를 잠시 밀어내고 진짜 새 트랜잭션을 열어야
    // 저장이 실제로 남는다. (Spring javadoc의 "Use PROPAGATION_REQUIRES_NEW for any transactional
    // operation that is called from here"가 가리키는 상황이 정확히 이것이다.)
    //
    // 나머지 10개 create*Notification이 REQUIRED인 것과 갈리는 지점이기도 하다 - 그쪽은 거래/문의 같은
    // 업무 트랜잭션 <b>안에서</b> 불리므로 업무가 롤백되면 알림도 함께 사라지는 게 맞다. 이 메서드는
    // 업무(결제·입찰)가 이미 커밋을 마친 뒤에 불리므로 같이 묶을 대상 자체가 없다.
    //
    // 주의: 그래서 이 메서드를 업무 트랜잭션 안에서 호출하면 의도와 다르게 동작한다 - 알림만 독립적으로
    // 먼저 커밋돼, 이후 그 업무가 롤백돼도 알림은 남는다. 새 호출부를 만들 때는 그 맥락이 AFTER_COMMIT인지
    // 먼저 확인할 것.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createBuyOfferReceivedNotification(
            List<Long> sellerIds, Long cardId, String cardName, Integer offeredPrice, Card card) {
        if (sellerIds == null || sellerIds.isEmpty()) {
            return;
        }

        String message = String.format(
                "%s 카드에 %,d원 구매 입찰이 등록되었습니다. 즉시판매로 바로 팔 수 있어요.", cardName, offeredPrice);
        List<Notification> notifications = sellerIds.stream()
                .map(sellerId -> Notification.builder()
                        .userId(sellerId)
                        .type(NotificationType.BUY_OFFER_RECEIVED)
                        .message(message)
                        .cardId(cardId)
                        .build())
                .toList();

        // createInquiryReceivedNotification과 같은 이유로 saveAll()이 돌려주는 인스턴스를 쓴다 -
        // id가 채워진 건 이쪽이라, 인자로 넘긴 목록으로 응답 DTO를 만들면 id가 null인 알림이 SSE로 나간다.
        for (Notification saved : notificationRepository.saveAll(notifications)) {
            eventPublisher.publishEvent(
                    new NotificationPushEvent(saved.getUserId(), NotificationResponse.of(saved, card)));
        }
    }

    // #392: "저장 → 커밋 후 푸시용 이벤트 발행"이라는 똑같은 절차를 공용화했다. 알림 레코드에 카드를
    // 붙이지 않는(=푸시 시점에 썸네일이 없는) 타입들이 쓴다 - 목록 조회 시 getNotifications()의 배치
    // 조회가 이미지를 채워준다.
    private void saveAndPublish(Long userId, NotificationType type, Long cardId, String message) {
        saveAndPublish(userId, type, cardId, message, null);
    }

    /**
     * 호출자가 이미 조회해 둔 {@link Card}가 있으면 그대로 넘겨 푸시 payload의 썸네일까지 채우는 오버로드.
     * 워치리스트 계열(PRICE_TARGET/LISTING_AVAILABLE)이 쓴다 - 목표가 판정·재입고 판정을 위해 카드를
     * 이미 로드해 둔 상태라 다시 조회할 이유가 없다.
     */
    private void saveAndPublish(Long userId, NotificationType type, Long cardId, String message, Card card) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .cardId(cardId)
                .build();

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationPushEvent(userId, NotificationResponse.of(notification, card)));
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
            // 여기서 실패하면 알림이 유실된 것이므로 하트비트와 분리된 카운터로 센다.
            sendEvent(emitter, SseEmitter.event().name("notification").data(response), PUSH_FAILURE_METRIC);
        }
    }

    // 유휴 상태로 프록시에 의해 연결이 끊기지 않도록 데이터 없는 코멘트만 주기적으로 보낸다.
    // 이 이벤트는 FE의 이벤트 리스너(onmessage 등)에 아무런 영향을 주지 않는다.
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    public void sendHeartbeat() {
        for (SseEmitter emitter : sseEmitterStore.findAll()) {
            // 하트비트 전송 실패율은 알림 push 실패와 분리해서 본다.
            sendEvent(emitter, SseEmitter.event().comment("heartbeat"), HEARTBEAT_FAILURE_METRIC);
        }
    }

    // 실패 카운터를 두지 않는 경로(subscribe()의 connect 더미 이벤트)용 오버로드.
    private void sendEvent(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        sendEvent(emitter, event, null);
    }

    // 하나의 Emitter 전송 실패가 나머지 Emitter 처리를 막지 않도록 예외를 여기서 흡수한다.
    // failureMetricName이 있을 때만 실패 카운터를 올린다.
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
