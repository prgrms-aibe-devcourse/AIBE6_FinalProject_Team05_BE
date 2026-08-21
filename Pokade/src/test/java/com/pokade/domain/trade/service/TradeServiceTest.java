package com.pokade.domain.trade.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.trade.dto.TradeReadyRequest;
import com.pokade.domain.trade.dto.TradeReadyResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeOrder;
import com.pokade.domain.trade.entity.TradeOrderStatus;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeOrderRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private UserAccessChecker userAccessChecker;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PointService pointService;

    @InjectMocks
    private TradeService tradeService;

    private Trade tradeOf(Long sellerId, Long buyerId) {
        Listing listing = Listing.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .price(10000)
                .build();

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

    private TradeOrder pendingOrderOf(Long buyerId, Long listingId, int amount) {
        return TradeOrder.builder()
                .orderId("order-1")
                .buyerId(buyerId)
                .listingId(listingId)
                .amount(amount)
                .build();
    }

    @Test
    void 결제준비시_구매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.ready(200L, new TradeReadyRequest(1L)))
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

        assertThatThrownBy(() -> tradeService.ready(200L, new TradeReadyRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_본인_매물이면_SELF_PURCHASE_NOT_ALLOWED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(listingRepository.findById(1L)).willReturn(Optional.of(trade.getListing()));

        assertThatThrownBy(() -> tradeService.ready(100L, new TradeReadyRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_PURCHASE_NOT_ALLOWED);

        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void 결제준비시_매물을_잠그지_않고_주문만_PENDING으로_기록한다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));

        TradeReadyResponse response = tradeService.ready(200L, new TradeReadyRequest(1L));

        assertThat(response.amount()).isEqualTo(10000);
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
    void 구매자_본인이_조회하면_거래정보를_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.getTrade(200L, 1L);

        assertThat(response.buyerId()).isEqualTo(200L);
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
        verify(pointService).settle(eq(100L), eq(10000), any());
    }

    @Test
    void 구매자가_아니면_확정시_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.confirmTrade(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
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
}
