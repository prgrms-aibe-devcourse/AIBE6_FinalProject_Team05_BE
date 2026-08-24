package com.pokade.domain.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.card.support.PokedexKoNameCache;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.domain.notification.event.NotificationPushEvent;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.price.entity.BuyOffer;
import com.pokade.domain.price.entity.BuyOfferOrder;
import com.pokade.domain.price.entity.BuyOfferOrderStatus;
import com.pokade.domain.price.repository.BuyOfferOrderRepository;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.price.store.PriceRankingRefreshStore;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.event.BuyOfferCreatedEvent;
import com.pokade.global.port.UserAccessChecker;
import com.pokade.support.AbstractIntegrationTest;
import com.pokade.support.TestMetricsConfig;

import jakarta.persistence.EntityManager;

// 구매입찰 등록 알림의 트랜잭션 배선을 검증한다(#417). 단위 테스트 세 개(리스너/NotificationService/
// PriceService)는 전부 mock 기반이라 "이벤트가 발행되는가", "리스너가 수신자를 어떻게 고르는가",
// "saveAll이 불리는가"까지만 본다 - 실제로 DB에 행이 남는지는 이 테스트가 아니면 알 수 없다.
//
// 이 테스트가 있어야 하는 진짜 이유: AFTER_COMMIT 콜백은 커밋 직후지만 아직 트랜잭션 정리 전이라
// EntityManagerHolder가 스레드에 그대로 바인딩돼 있다. 그 상태에서 알림 저장을 PROPAGATION_REQUIRED로
// 하면 새 트랜잭션이 열리는 게 아니라 이미 커밋된 트랜잭션에 참여만 하게 되고, 참여 트랜잭션은
// isNewTransaction()이 false라 커밋 자체를 건너뛴다 - saveAll이 조용히 사라진다. mock 기반 테스트는
// 전부 초록인 채로 이 결함을 통과시킨다(실제로 한 번 통과시켰다). 그래서 커밋 이후 DB를 다시 읽는
// 검증이 필요하다.
//
// 슬라이스 구성과 클래스 트랜잭션을 끄는 이유는 WatchlistListingAvailableNoticeIntegrationTest와 동일하다
// (전체 컨텍스트는 S3/메일/OAuth2 자격 증명을 요구하고, @DataJpaTest 기본 테스트 트랜잭션 위에서는
// TransactionTemplate로 연 트랜잭션도 물리적으로 커밋되지 않아 AFTER_COMMIT이 아예 발동하지 않는다).
//
// PriceService까지 함께 올리는 이유: 위 세 테스트는 eventPublisher.publishEvent로 "발행 지점만 흉내"내므로
// 정작 그 이벤트를 실제로 발행하는 업무 서비스(confirmBuyOfferPurchase) 앞단은 한 번도 타지 않는다.
// 즉 "업무 서비스가 자기 트랜잭션 안에서 이벤트를 발행한다"는 전제 자체는 어디서도 검증되지 않는다
// (PriceServiceTest는 mock 기반이라 publishEvent 호출만 본다). 마지막 테스트가 그 구멍을 메운다.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BuyOfferReceivedNoticeListener.class, NotificationService.class, CardNameKoResolver.class,
        PokedexKoNameCache.class, SseEmitterStore.class, TestMetricsConfig.class, PriceService.class,
        BuyOfferReceivedNoticeIntegrationTest.PushEventRecorderConfig.class})
class BuyOfferReceivedNoticeIntegrationTest extends AbstractIntegrationTest {

    // SSE 푸시 검증용. NotificationService가 발행하는 NotificationPushEvent를 실제 SSE 대신 여기 모은다.
    // 일부러 @TransactionalEventListener(AFTER_COMMIT)으로 받는다 - 평범한 @EventListener로 받으면
    // "발행됐다"까지만 보이지만, 이 방식은 알림 트랜잭션이 실제로 커밋됐을 때만 기록된다. 즉 저장이
    // 사라지는 시나리오에서는 이쪽도 비게 되어 같은 결함을 두 각도에서 잡는다.
    static class PushEventRecorder {

        private final List<NotificationPushEvent> received = new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(NotificationPushEvent event) {
            received.add(event);
        }

        List<NotificationPushEvent> received() {
            return received;
        }

        void clear() {
            received.clear();
        }
    }

    @TestConfiguration
    static class PushEventRecorderConfig {

        @Bean
        PushEventRecorder pushEventRecorder() {
            return new PushEventRecorder();
        }
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushEventRecorder pushEventRecorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PriceService priceService;

    @Autowired
    private BuyOfferOrderRepository buyOfferOrderRepository;

    @Autowired
    private BuyOfferRepository buyOfferRepository;

    // PriceService의 협력자 중 DB 밖에 있는 것들만 잘라낸다. 잘라낸 기준:
    // - tossPaymentClient: 외부 HTTP. 다만 "결제금액 > 0" 분기는 그대로 태우고 호출만 스텁한다
    //   (금액을 0으로 맞춰 분기를 건너뛰면 정작 검증하려는 실제 결제 경로가 빠진다).
    // - pointService: 포인트 원장까지 세팅하는 비용이 이 테스트의 관심사(알림 배선)에 비해 크다.
    //   대신 주문의 pointsUsed를 0으로 둬서 애초에 호출되지 않는 조건으로 맞춘다.
    // - tradeService/priceRankingRefreshStore(Redis)/userAccessChecker: confirmBuyOfferPurchase
    //   경로에서 아예 호출되지 않는다. 빈이 없으면 컨텍스트가 안 뜨므로 자리만 채운다.
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private PointService pointService;

    @MockitoBean
    private TradeService tradeService;

    @MockitoBean
    private PriceRankingRefreshStore priceRankingRefreshStore;

    @MockitoBean
    private UserAccessChecker userAccessChecker;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    // 클래스 트랜잭션을 껐다는 건 이 테스트가 넣은 데이터가 자동으로 롤백되지 않는다는 뜻이고,
    // AbstractIntegrationTest의 PostgreSQLContainer는 static이라 JVM 안의 모든 테스트 클래스가 같은
    // DB를 공유한다. 그냥 두면 여기서 만든 카드 3장이 CardRepositoryTest처럼 "카드 전체 건수"를
    // 절대값으로 단언하는 테스트를 깨뜨린다(실제로 9건이 깨지는 것을 확인했다). 그래서 이 테스트가
    // 만든 행만 접두사로 골라 직접 지운다 - FK 때문에 notifications → listings → card_variants →
    // cards → users 순서를 지켜야 한다.
    @AfterEach
    void cleanUpOwnData() {
        transactionTemplate().executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM notifications WHERE user_id IN "
                    + "(SELECT id FROM users WHERE email LIKE 'bo-notice-%')").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM buy_offers WHERE buyer_id IN "
                    + "(SELECT id FROM users WHERE email LIKE 'bo-notice-%')").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM buy_offer_orders WHERE buyer_id IN "
                    + "(SELECT id FROM users WHERE email LIKE 'bo-notice-%')").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM listings WHERE card_id IN "
                    + "(SELECT id FROM cards WHERE external_id LIKE 'bo-notice-card-%')").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM card_variants WHERE card_id IN "
                    + "(SELECT id FROM cards WHERE external_id LIKE 'bo-notice-card-%')").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM cards WHERE external_id LIKE 'bo-notice-card-%'")
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE email LIKE 'bo-notice-%'").executeUpdate();
        });
        pushEventRecorder.clear();
    }

    // 클래스 트랜잭션을 껐으므로 준비 데이터도 각자 자기 트랜잭션에서 커밋해야 한다.
    private Long persistActiveUser(String email) {
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue());
    }

    private Long persistCard(String externalId, String name) {
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, :name) RETURNING id")
                .setParameter("externalId", externalId)
                .setParameter("name", name)
                .getSingleResult()).longValue());
    }

    private Long persistVariant(Long cardId, String variantName) {
        return transactionTemplate().execute(status -> ((Number) entityManager.createNativeQuery(
                        "INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) "
                                + "VALUES (:cardId, :variantName, true, now()) RETURNING id")
                .setParameter("cardId", cardId)
                .setParameter("variantName", variantName)
                .getSingleResult()).longValue());
    }

    private void persistActiveListing(Long cardId, Long sellerId, Long variantId, int price) {
        transactionTemplate().executeWithoutResult(status -> entityManager.createNativeQuery(
                        "INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status) "
                                + "VALUES (:cardId, :sellerId, :variantId, :price, 'S', 'ACTIVE')")
                .setParameter("cardId", cardId)
                .setParameter("sellerId", sellerId)
                .setParameter("variantId", variantId)
                .setParameter("price", price)
                .executeUpdate());
    }

    // PriceService.confirmBuyOfferPurchase가 하는 일을 그대로 재현한다 - 업무 트랜잭션 안에서 이벤트를
    // 발행하고 그 트랜잭션을 커밋한다. 결제/포인트 협력자까지 끌어오면 슬라이스가 통째로 무거워지므로
    // 발행 지점만 같은 조건으로 맞춘다(발행 자체는 PriceServiceTest t69가 따로 고정한다).
    private void publishInCommittedTransaction(BuyOfferCreatedEvent event) {
        transactionTemplate().executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private List<Notification> notificationsOf(Long userId) {
        return notificationRepository.findAllByUserIdForTestVerification(userId);
    }

    @Test
    void 커밋되면_매물을_가진_판매자들에게_알림이_실제로_저장된다() {
        Long buyerId = persistActiveUser("bo-notice-buyer1@test.com");
        Long seller1 = persistActiveUser("bo-notice-seller1a@test.com");
        Long seller2 = persistActiveUser("bo-notice-seller1b@test.com");
        Long cardId = persistCard("bo-notice-card-1", "Charizard");
        Long variantId = persistVariant(cardId, "holo-1");
        // seller1은 같은 카드에 매물 2건 - 중복 제거가 DB 저장 단계까지 유지되는지 함께 본다.
        persistActiveListing(cardId, seller1, variantId, 140000);
        persistActiveListing(cardId, seller1, variantId, 145000);
        persistActiveListing(cardId, seller2, variantId, 150000);

        publishInCommittedTransaction(
                new BuyOfferCreatedEvent(1L, cardId, variantId, ListingGrade.S, buyerId, 150000));

        assertThat(notificationsOf(seller1)).hasSize(1);
        assertThat(notificationsOf(seller2)).hasSize(1);

        Notification saved = notificationsOf(seller1).get(0);
        assertThat(saved.getType()).isEqualTo(NotificationType.BUY_OFFER_RECEIVED);
        assertThat(saved.getCardId()).isEqualTo(cardId);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMessage()).contains("150,000");
        // 입찰자 본인은 매물이 없으므로 애초에 대상이 아니다 - 알림이 새어 들어가지 않았는지 확인.
        assertThat(notificationsOf(buyerId)).isEmpty();
    }

    @Test
    void 이벤트를_발행한_트랜잭션이_롤백되면_알림이_생기지_않는다() {
        Long buyerId = persistActiveUser("bo-notice-buyer2@test.com");
        Long sellerId = persistActiveUser("bo-notice-seller2@test.com");
        Long cardId = persistCard("bo-notice-card-2", "Blastoise");
        Long variantId = persistVariant(cardId, "holo-2");
        persistActiveListing(cardId, sellerId, variantId, 150000);

        transactionTemplate().executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new BuyOfferCreatedEvent(2L, cardId, variantId, ListingGrade.S, buyerId, 150000));
            status.setRollbackOnly();
        });

        assertThat(notificationsOf(sellerId)).isEmpty();
    }

    @Test
    void 알림이_저장되면_SSE_푸시_이벤트가_수신자마다_발행된다() {
        Long buyerId = persistActiveUser("bo-notice-buyer3@test.com");
        Long seller1 = persistActiveUser("bo-notice-seller3a@test.com");
        Long seller2 = persistActiveUser("bo-notice-seller3b@test.com");
        Long cardId = persistCard("bo-notice-card-3", "Pikachu");
        Long variantId = persistVariant(cardId, "holo-3");
        persistActiveListing(cardId, seller1, variantId, 140000);
        persistActiveListing(cardId, seller2, variantId, 150000);
        pushEventRecorder.clear();

        publishInCommittedTransaction(
                new BuyOfferCreatedEvent(3L, cardId, variantId, ListingGrade.S, buyerId, 150000));

        assertThat(pushEventRecorder.received())
                .hasSize(2)
                .extracting(NotificationPushEvent::userId)
                .containsExactlyInAnyOrder(seller1, seller2);
        // 푸시 payload는 저장된 행 기준이라 id가 채워져 있어야 한다(저장 전 인스턴스를 쓰면 null이 나간다).
        assertThat(pushEventRecorder.received())
                .allSatisfy(event -> assertThat(event.response().id()).isNotNull());
    }

    // 위 세 테스트가 흉내로 대신했던 앞단 - PriceService.confirmBuyOfferPurchase를 실제로 호출한다.
    // 여기서만 검증되는 것: 업무 서비스가 자기 @Transactional 안에서 BuyOfferCreatedEvent를 발행하고,
    // 그 트랜잭션이 물리적으로 커밋되면서 AFTER_COMMIT 리스너가 붙는다는 배선 전체. publishEvent를
    // 테스트가 직접 부르는 방식으로는 "서비스가 이벤트 발행을 빠뜨렸다" 같은 회귀를 잡을 수 없다.
    //
    // 포인트 사용액을 0으로 두는 건 pointService(mock)를 타지 않게 하려는 것이고, 결제금액은 일부러
    // 0보다 크게 둬서 토스 승인 분기까지 실제로 통과시킨다(호출 자체만 스텁).
    @Test
    void confirmBuyOfferPurchase가_커밋되면_판매자에게_알림이_저장된다() {
        Long buyerId = persistActiveUser("bo-notice-buyer4@test.com");
        Long sellerId = persistActiveUser("bo-notice-seller4@test.com");
        Long cardId = persistCard("bo-notice-card-4", "Mewtwo");
        Long variantId = persistVariant(cardId, "holo-4");
        persistActiveListing(cardId, sellerId, variantId, 160000);

        String orderId = UUID.randomUUID().toString();
        transactionTemplate().executeWithoutResult(status -> buyOfferOrderRepository.save(BuyOfferOrder.builder()
                .orderId(orderId)
                .buyerId(buyerId)
                .cardId(cardId)
                .variantId(variantId)
                .grade(ListingGrade.S)
                .price(150000)
                .shippingFee(3000)
                .pointsUsed(0)
                .recipientName("받는사람")
                .recipientPhone("010-0000-0000")
                .recipientAddress("서울시 어딘가 1-1")
                .build()));

        priceService.confirmBuyOfferPurchase(buyerId, "test-payment-key", orderId, 153000L);

        List<Notification> notifications = notificationsOf(sellerId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.BUY_OFFER_RECEIVED);
        assertThat(notifications.get(0).getCardId()).isEqualTo(cardId);
        // 알림 문구의 금액은 배송비를 뺀 상품가(=판매자 정산 기준가)여야 한다.
        assertThat(notifications.get(0).getMessage()).contains("150,000");
        // 입찰자 본인에게는 가지 않는다(매물이 없어 애초에 대상이 아님).
        assertThat(notificationsOf(buyerId)).isEmpty();

        // 알림만 보고 끝내면 "결제 경로가 실제로 끝까지 갔는지"는 알 수 없으므로 업무 결과도 함께 본다.
        List<BuyOffer> buyOffers = buyOfferRepository.findAll().stream()
                .filter(buyOffer -> buyerId.equals(buyOffer.getBuyerId()))
                .toList();
        assertThat(buyOffers).hasSize(1);
        assertThat(buyOffers.get(0).getCardId()).isEqualTo(cardId);
        assertThat(buyOfferOrderRepository.findByOrderId(orderId))
                .get()
                .extracting(BuyOfferOrder::getStatus)
                .isEqualTo(BuyOfferOrderStatus.CONFIRMED);
        verify(tossPaymentClient, times(1)).confirmPayment("test-payment-key", orderId, 153000);
    }
}
