package com.pokade.domain.trade.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.portfolio.service.PortfolioService;
import com.pokade.domain.trade.dto.TradeReadyRequest;
import com.pokade.domain.trade.dto.TradeReadyResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeOrder;
import com.pokade.domain.trade.entity.TradeOrderStatus;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeOrderRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardNameKoResolver cardNameKoResolver;

    @Mock
    private UserAccessChecker userAccessChecker;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PointService pointService;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TradeService tradeService;

    private Trade tradeOf(Long sellerId, Long buyerId) {
        Listing listing = Listing.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .price(10000)
                .build();
        // 실제로 Trade가 존재한다는 것 자체가 markAsTrading()이 이미 성공했다는 뜻이라, 매물은 항상
        // TRADING 상태다(빌더 기본값 ACTIVE는 여기서는 맞지 않는다) - markSold() 등 TRADING을 전제하는
        // 로직을 테스트하려면 이 픽스처가 실제 불변조건을 반영해야 한다.
        ReflectionTestUtils.setField(listing, "status", ListingStatus.TRADING);

        return Trade.builder()
                .listing(listing)
                .buyerId(buyerId)
                .price(10000)
                .build();
    }

    // 구매확정은 DELIVERED 상태에서만 가능해서, 확정/완료 관련 테스트는 발송·검수·배송 단계를 먼저 거친다.
    private Trade deliveredTradeOf(Long sellerId, Long buyerId) {
        Trade trade = tradeOf(sellerId, buyerId);
        trade.shipToPlatform();
        trade.markInspected();
        trade.markDelivered();
        return trade;
    }

    private Payment paymentOf(Trade trade, Long buyerId) {
        return Payment.builder()
                .trade(trade)
                .buyerId(buyerId)
                .amount(trade.getPrice())
                .method(com.pokade.domain.trade.entity.PaymentMethod.CARD)
                .tossPaymentKey("pay_123")
                .build();
    }

    // 포인트로 전액을 충당해 토스 결제 자체가 없었던 거래 - tossPaymentKey가 없다.
    private Payment fullPointsPaymentOf(Trade trade, Long buyerId, int pointsUsed) {
        return Payment.builder()
                .trade(trade)
                .buyerId(buyerId)
                .amount(0)
                .pointsUsed(pointsUsed)
                .method(com.pokade.domain.trade.entity.PaymentMethod.CARD)
                .build();
    }

    private TradeOrder pendingOrderOf(Long buyerId, Long listingId, int amount) {
        return TradeOrder.builder()
                .orderId("order-1")
                .buyerId(buyerId)
                .listingId(listingId)
                .amount(amount)
                .build();
    }

    private TradeOrder pendingOrderWithPointsOf(Long buyerId, Long listingId, int amount, int pointsUsed) {
        return TradeOrder.builder()
                .orderId("order-1")
                .buyerId(buyerId)
                .listingId(listingId)
                .amount(amount)
                .pointsUsed(pointsUsed)
                .build();
    }

    @Test
    void 결제준비시_구매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.ready(200L, new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(listingRepository, never()).findById(anyLong());
    }

    @Test
    void 결제준비시_판매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(listingRepository.findById(1L)).willReturn(Optional.of(trade.getListing()));
        willDoNothing().given(userAccessChecker).assertWritable(200L);
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(100L);

        assertThatThrownBy(() -> tradeService.ready(200L, new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_본인_매물이면_SELF_PURCHASE_NOT_ALLOWED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(listingRepository.findById(1L)).willReturn(Optional.of(trade.getListing()));

        assertThatThrownBy(() -> tradeService.ready(100L, new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_PURCHASE_NOT_ALLOWED);

        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_매물을_잠그지_않고_주문만_PENDING으로_기록한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));

        TradeReadyResponse response = tradeService.ready(200L, new TradeReadyRequest(1L, 0, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1"));

        assertThat(response.amount()).isEqualTo(13000);
        assertThat(response.orderId()).isNotBlank();
        verify(listingRepository, never()).markAsTrading(any());
        verify(tradeOrderRepository).save(any(TradeOrder.class));
    }

    @Test
    void 결제승인시_주문이_없으면_TRADE_ORDER_NOT_FOUND_예외가_발생한다() {
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_ORDER_NOT_FOUND);
    }

    @Test
    void 결제승인시_주문의_구매자가_아니면_ACCESS_DENIED_예외가_발생한다() {
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> tradeService.confirmPurchase(999L, "pay_123", "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 결제승인시_이미_처리된_주문이면_TRADE_ORDER_ALREADY_PROCESSED_예외가_발생한다() {
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        order.markConfirmed();
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_ORDER_ALREADY_PROCESSED);
    }

    @Test
    void 결제승인시_금액이_다르면_INVALID_INPUT_예외가_발생하고_토스_승인을_호출하지_않는다() {
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, "pay_123", "order-1", 9999))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
    }

    @Test
    void 결제승인_성공시_매물을_잠그고_거래와_결제를_생성한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        TradeResponse response = tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000);

        assertThat(response.buyerId()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo(TradeStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(TradeOrderStatus.CONFIRMED);
        verify(tossPaymentClient).confirmPayment("pay_123", "order-1", 10000L);
        verify(paymentRepository).save(any(Payment.class));
        verify(tossPaymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    void 결제승인_실패시_주문을_FAILED로_기록하고_예외를_다시_던진다() {
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED))
                .given(tossPaymentClient).confirmPayment(any(), any(), any(Long.class));

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED);

        verify(tradeOrderRepository).markFailedIfPending("order-1");
        verify(listingRepository, never()).markAsTrading(any());
    }

    @Test
    void 결제는_승인됐지만_매물이_이미_팔렸으면_결제를_취소하고_TRADE_CONFLICT를_던진다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(0);

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_CONFLICT);

        verify(tossPaymentClient).cancelPayment(eq("pay_123"), anyString());
        verify(tradeOrderRepository).markFailedIfPending("order-1");
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void 결제준비시_포인트_사용액이_결제_금액보다_크면_INVALID_INPUT_예외가_발생한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));

        assertThatThrownBy(() -> tradeService.ready(
                200L, new TradeReadyRequest(1L, 999999, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_포인트_잔액이_부족하면_INSUFFICIENT_POINT_BALANCE_예외가_발생한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(userRepository.findById(200L)).willReturn(Optional.of(User.builder().pointBalance(1000).build()));

        assertThatThrownBy(() -> tradeService.ready(
                200L, new TradeReadyRequest(1L, 5000, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_POINT_BALANCE);
        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_포인트_사용액만큼_뺀_금액을_결제_금액으로_반환하고_주문에_남긴다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(userRepository.findById(200L)).willReturn(Optional.of(User.builder().pointBalance(100000).build()));

        TradeReadyResponse response = tradeService.ready(
                200L, new TradeReadyRequest(1L, 5000, "김철수", "010-1234-5678", "서울시 강남구 테헤란로 1"));

        assertThat(response.amount()).isEqualTo(8000);
        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(tradeOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getPointsUsed()).isEqualTo(5000);
    }

    @Test
    void 결제승인시_사용한_포인트만큼_pointService_use를_호출한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderWithPointsOf(200L, 1L, 13000, 5000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        tradeService.confirmPurchase(200L, "pay_123", "order-1", 8000);

        verify(pointService).use(200L, 5000, null);
        verify(tossPaymentClient).confirmPayment("pay_123", "order-1", 8000L);
    }

    @Test
    void 포인트로_전액을_충당해_결제_금액이_0이면_토스_승인_없이_거래를_생성한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderWithPointsOf(200L, 1L, 13000, 13000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        TradeResponse response = tradeService.confirmPurchase(200L, null, "order-1", 0);

        assertThat(response.status()).isEqualTo(TradeStatus.PENDING);
        verify(pointService).use(200L, 13000, null);
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getTossPaymentKey()).isNull();
    }

    @Test
    void 결제_금액이_남아있는데_paymentKey가_없으면_INVALID_INPUT_예외가_발생하고_토스_승인을_호출하지_않는다() {
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> tradeService.confirmPurchase(200L, null, "order-1", 10000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any(Long.class));
        verify(pointService, never()).use(any(), anyInt(), any());
    }

    @Test
    void 전액_포인트로_결제한_거래를_취소하면_토스_취소_없이_포인트를_환불한다() {
        Trade trade = tradeOf(100L, 200L);
        Payment payment = fullPointsPaymentOf(trade, 200L, 13000);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(payment));

        TradeResponse response = tradeService.cancelTrade(200L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.CANCELLED);
        verify(tossPaymentClient, never()).cancelPayment(any(), any());
        verify(pointService).refund(200L, 13000, trade.getId());
        assertThat(payment.getStatus()).isEqualTo(com.pokade.domain.trade.entity.PaymentStatus.REFUNDED);
    }

    @Test
    void 구매자_본인이_조회하면_거래정보를_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.getTrade(200L, 1L);

        assertThat(response.buyerId()).isEqualTo(200L);
    }

    @Test
    void 조회시_결제에_사용된_포인트를_함께_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(
                Payment.builder()
                        .trade(trade)
                        .buyerId(200L)
                        .amount(trade.getPrice())
                        .pointsUsed(5000)
                        .method(com.pokade.domain.trade.entity.PaymentMethod.CARD)
                        .tossPaymentKey("pay_123")
                        .build()));

        TradeResponse response = tradeService.getTrade(200L, 1L);

        assertThat(response.pointsUsed()).isEqualTo(5000);
    }

    @Test
    void 판매자_본인이_조회하면_거래정보를_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.getTrade(100L, 1L);

        assertThat(response.buyerId()).isEqualTo(200L);
    }

    @Test
    void 구매자도_판매자도_아니면_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.getTrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래를_조회하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.getTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 확정시_구매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeRepository, never()).findById(anyLong());
    }

    @Test
    void 구매자가_확정하면_거래상태가_COMPLETED로_바뀌고_판매자에게_정산된다() {
        Trade trade = deliveredTradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(paymentOf(trade, 200L)));

        TradeResponse response = tradeService.confirmTrade(200L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.COMPLETED);
        // 구매확정 이후 매물이 TRADING에 계속 남아있으면 "내 매물 관리"에서 영원히 거래중으로 보인다.
        assertThat(trade.getListing().getStatus()).isEqualTo(com.pokade.domain.listing.entity.ListingStatus.SOLD);
        verify(pointService).settle(eq(100L), eq(10000), any());
        // #392: 정산과 같은 지점에서 판매자(100L)에게 알림이 나가야 한다 - 구매자가 아니라 판매자다.
        verify(notificationService).createTradeConfirmedNotification(eq(100L), any(), any(), eq(10000));
    }

    @Test
    void 구매자가_아니면_확정시_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.confirmTrade(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        // #392: 확정이 거부되면 정산도 알림도 없어야 한다(유령 정산 알림 방지).
        verify(notificationService, never()).createTradeConfirmedNotification(any(), any(), any(), any());
    }

    @Test
    void 존재하지_않는_거래를_확정하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 이미_완료된_거래를_다시_확정하면_INVALID_TRADE_STATUS_예외가_발생한다() {
        Trade trade = deliveredTradeOf(100L, 200L);
        trade.complete();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);
    }

    @Test
    void 취소시_액션_주체_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeRepository, never()).findById(anyLong());
    }

    @Test
    void 구매자가_취소하면_거래상태가_CANCELLED로_바뀌고_토스_결제가_취소된다() {
        Trade trade = tradeOf(100L, 200L);
        Payment payment = paymentOf(trade, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(payment));

        TradeResponse response = tradeService.cancelTrade(200L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.CANCELLED);
        verify(tossPaymentClient).cancelPayment(eq("pay_123"), anyString());
        assertThat(payment.getStatus()).isEqualTo(com.pokade.domain.trade.entity.PaymentStatus.REFUNDED);
        verify(listingRepository).revertToActiveIfTrading(trade.getListing().getId());
    }

    @Test
    void 판매자가_취소하면_거래상태가_CANCELLED로_바뀌고_토스_결제가_취소된다() {
        Trade trade = tradeOf(100L, 200L);
        Payment payment = paymentOf(trade, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(payment));

        TradeResponse response = tradeService.cancelTrade(100L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.CANCELLED);
        verify(tossPaymentClient).cancelPayment(eq("pay_123"), anyString());
    }

    @Test
    void 구매자도_판매자도_아니면_취소시_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래를_취소하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 이미_완료된_거래를_취소하면_INVALID_TRADE_STATUS_예외가_발생하고_토스_결제가_취소되지_않는다() {
        Trade trade = deliveredTradeOf(100L, 200L);
        trade.complete();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);

        verify(tossPaymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    void 배송_완료된_거래를_취소하면_INVALID_TRADE_STATUS_예외가_발생하고_토스_결제가_취소되지_않는다() {
        // 구매확정(COMPLETED) 전이라도 배송이 완료됐다면(실물 수령) 취소·환불을 막아야 한다 -
        // 안 그러면 카드를 받고도 결제를 환불받아가는 경로가 생긴다.
        Trade trade = deliveredTradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);

        verify(tossPaymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    void 판매자가_발송처리하면_거래상태가_SHIPPED_TO_PLATFORM으로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.shipTrade(100L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.SHIPPED_TO_PLATFORM);
    }

    @Test
    void 발송시_판매자_본인이_아니면_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.shipTrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 발송시_판매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(100L);

        assertThatThrownBy(() -> tradeService.shipTrade(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeRepository, never()).findById(anyLong());
    }

    @Test
    void 발송_대기가_아닌_거래를_발송처리하면_INVALID_TRADE_STATUS_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        trade.shipToPlatform();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.shipTrade(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);
    }

    @Test
    void 검수_배송_대기_거래가_없으면_빈_목록을_반환한다() {
        given(tradeRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(TradeStatus.SHIPPED_TO_PLATFORM, TradeStatus.INSPECTED)))
                .willReturn(List.of());

        List<TradeResponse> responses = tradeService.getPendingTrades();

        assertThat(responses).isEmpty();
    }

    @Test
    void 검수_배송_대기_거래_목록을_반환한다() {
        Trade shipped = tradeOf(100L, 200L);
        shipped.shipToPlatform();
        Trade inspected = tradeOf(100L, 200L);
        inspected.shipToPlatform();
        inspected.markInspected();
        given(tradeRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(TradeStatus.SHIPPED_TO_PLATFORM, TradeStatus.INSPECTED)))
                .willReturn(List.of(shipped, inspected));

        List<TradeResponse> responses = tradeService.getPendingTrades();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).status()).isEqualTo(TradeStatus.SHIPPED_TO_PLATFORM);
        assertThat(responses.get(1).status()).isEqualTo(TradeStatus.INSPECTED);
    }

    @Test
    void 관리자가_검수처리하면_거래상태가_INSPECTED로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        trade.shipToPlatform();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.markInspected(1L);

        assertThat(response.status()).isEqualTo(TradeStatus.INSPECTED);
    }

    @Test
    void 관리자가_배송처리하면_거래상태가_DELIVERED로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        trade.shipToPlatform();
        trade.markInspected();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.markDelivered(1L);

        assertThat(response.status()).isEqualTo(TradeStatus.DELIVERED);
    }

    // ===== #392: 거래 단계별 알림 =====
    // 픽스처의 tradeOf(sellerId=100, buyerId=200) 규약을 그대로 따른다.
    // 공통 원칙: "방금 행동한 사람"에게는 보내지 않는다. 단 발송 요청만은 예외로, 즉시판매처럼
    // 판매자가 직접 행동한 경우에도 보낸다(며칠 뒤 알림함에서 다시 보는 것이 목적이라서).

    @Test
    void 즉시구매_결제가_승인되면_판매자에게만_발송요청_알림이_간다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000);

        verify(notificationService).createTradeShippingRequiredNotification(eq(100L), eq(1L), any());
        // 구매자(200L)는 방금 자기가 결제한 참이라 알림 대상이 아니다.
        verify(notificationService, never()).createBuyOfferMatchedNotification(any(), any(), any(), any());
    }

    @Test
    void 즉시판매로_체결되면_판매자에게는_발송요청_입찰자에게는_체결_알림이_각각_간다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.markAsTrading(1L)).willReturn(1);
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        tradeService.createMatchedTrade(1L, 200L, 10000, 13000,
                "김철수", "010-1234-5678", "서울시 강남구", "pay_123", 0);

        // 한 사건에서 수신자가 다른 알림 2건이 나간다 - 트리거로 구분하는 게 아니라 수신자별로 타입이 다르다.
        verify(notificationService).createTradeShippingRequiredNotification(eq(100L), eq(1L), any());
        verify(notificationService).createBuyOfferMatchedNotification(eq(200L), eq(1L), any(), eq(10000));
    }

    @Test
    void 관리자가_배송처리하면_구매자에게만_구매확정_요청_알림이_간다() {
        Trade trade = tradeOf(100L, 200L);
        trade.shipToPlatform();
        trade.markInspected();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        tradeService.markDelivered(1L);

        verify(notificationService).createTradeDeliveredNotification(eq(200L), eq(1L), any());
    }

    @Test
    void 구매자가_취소하면_판매자에게_판매자용_문구로_알림이_간다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(paymentOf(trade, 200L)));

        tradeService.cancelTrade(200L, 1L);

        // 마지막 인자 false = 수신자가 구매자가 아님(판매자) → "매물이 다시 판매 중" 문구.
        verify(notificationService).createTradeCancelledNotification(eq(100L), eq(1L), any(), eq(false));
    }

    @Test
    void 판매자가_취소하면_구매자에게_구매자용_문구로_알림이_간다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));
        given(paymentRepository.findByTradeId(any())).willReturn(Optional.of(paymentOf(trade, 200L)));

        tradeService.cancelTrade(100L, 1L);

        // 마지막 인자 true = 수신자가 구매자 → "환불됩니다" 문구.
        verify(notificationService).createTradeCancelledNotification(eq(200L), eq(1L), any(), eq(true));
    }

    @Test
    void 참여자가_아닌_사람이_취소를_시도하면_알림이_가지_않는다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(999L, 1L))
                .isInstanceOf(BusinessException.class);

        verify(notificationService, never()).createTradeCancelledNotification(any(), any(), any(), anyBoolean());
    }

    @Test
    void 배송완료_이후에는_취소가_막히고_알림도_가지_않는다() {
        Trade trade = deliveredTradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);

        verify(notificationService, never()).createTradeCancelledNotification(any(), any(), any(), anyBoolean());
    }

    @Test
    void 알림_생성이_실패해도_거래_처리는_그대로_완료된다() {
        // 알림은 부가 기능이라, 이미 토스 승인이 끝난 결제를 알림 실패로 되돌리면 안 된다.
        Listing listing = tradeOf(100L, 200L).getListing();
        TradeOrder order = pendingOrderOf(200L, 1L, 10000);
        given(tradeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));
        willThrow(new RuntimeException("알림 저장 실패"))
                .given(notificationService).createTradeShippingRequiredNotification(any(), any(), any());

        TradeResponse response = tradeService.confirmPurchase(200L, "pay_123", "order-1", 10000);

        assertThat(response.status()).isEqualTo(TradeStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(TradeOrderStatus.CONFIRMED);
        verify(paymentRepository).save(any(Payment.class));
    }
}
