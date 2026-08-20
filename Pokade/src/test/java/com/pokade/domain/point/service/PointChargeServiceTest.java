package com.pokade.domain.point.service;

import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.client.dto.TossConfirmResponse;
import com.pokade.domain.point.dto.request.PointChargeReadyRequest;
import com.pokade.domain.point.dto.response.PointChargeReadyResponse;
import com.pokade.domain.point.entity.PointChargeOrder;
import com.pokade.domain.point.entity.PointChargeOrderStatus;
import com.pokade.domain.point.repository.PointChargeOrderRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PointChargeServiceTest {

    @Mock
    private PointChargeOrderRepository pointChargeOrderRepository;
    @Mock
    private TossPaymentClient tossPaymentClient;
    @Mock
    private PointService pointService;

    @InjectMocks
    private PointChargeService pointChargeService;

    private PointChargeOrder order(Long userId, int amount) {
        return PointChargeOrder.builder().orderId("order-1").userId(userId).amount(amount).build();
    }

    @Test
    @DisplayName("ready: 새 주문을 PENDING으로 저장하고 orderId/amount를 반환한다")
    void ready_savesPendingOrder() {
        PointChargeReadyResponse response = pointChargeService.ready(1L, new PointChargeReadyRequest(10000));

        assertThat(response.amount()).isEqualTo(10000);
        assertThat(response.orderId()).isNotBlank();

        ArgumentCaptor<PointChargeOrder> captor = ArgumentCaptor.forClass(PointChargeOrder.class);
        then(pointChargeOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getAmount()).isEqualTo(10000);
        assertThat(captor.getValue().getStatus()).isEqualTo(PointChargeOrderStatus.PENDING);
    }

    @Test
    @DisplayName("confirm: 정상 승인이면 토스를 호출하고 주문을 CONFIRMED로, 포인트를 충전한다")
    void confirm_success_chargesPoints() {
        PointChargeOrder order = order(1L, 10000);
        given(pointChargeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        given(tossPaymentClient.confirmPayment("pay-1", "order-1", 10000L))
                .willReturn(new TossConfirmResponse("pay-1", "order-1", "DONE", 10000L));
        given(pointService.charge(1L, 10000)).willReturn(20000);

        int balance = pointChargeService.confirm(1L, "pay-1", "order-1", 10000L);

        assertThat(balance).isEqualTo(20000);
        assertThat(order.getStatus()).isEqualTo(PointChargeOrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm: 존재하지 않는 주문이면 POINT_CHARGE_ORDER_NOT_FOUND를 던진다")
    void confirm_missingOrder_throws() {
        given(pointChargeOrderRepository.findByOrderId("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pointChargeService.confirm(1L, "pay-1", "missing", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_CHARGE_ORDER_NOT_FOUND);
        then(tossPaymentClient).should(never()).confirmPayment(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("confirm: 주문 소유자가 아니면 ACCESS_DENIED를 던지고 토스를 호출하지 않는다")
    void confirm_otherUsersOrder_throwsAccessDenied() {
        PointChargeOrder order = order(1L, 10000);
        given(pointChargeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> pointChargeService.confirm(999L, "pay-1", "order-1", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        then(tossPaymentClient).should(never()).confirmPayment(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("confirm: 클라이언트가 보낸 금액이 최초 요청 금액과 다르면 토스를 호출하지 않고 거부한다")
    void confirm_amountMismatch_rejectsWithoutCallingToss() {
        PointChargeOrder order = order(1L, 10000);
        given(pointChargeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> pointChargeService.confirm(1L, "pay-1", "order-1", 99999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        then(tossPaymentClient).should(never()).confirmPayment(anyString(), anyString(), anyLong());
        then(pointService).should(never()).charge(any(), anyInt());
    }

    @Test
    @DisplayName("confirm: 토스 승인이 실패하면 별도 트랜잭션으로 주문을 FAILED 기록하고 예외를 그대로 전파한다")
    void confirm_tossFails_marksOrderFailedInSeparateTransaction() {
        PointChargeOrder order = order(1L, 10000);
        given(pointChargeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));
        willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED))
                .given(tossPaymentClient).confirmPayment("pay-1", "order-1", 10000L);

        assertThatThrownBy(() -> pointChargeService.confirm(1L, "pay-1", "order-1", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED);
        // markFailed()를 같은 트랜잭션 안에서 엔티티만 바꾸면 이 메서드가 예외를 다시 던질 때 함께
        // 롤백되어 유실된다 - 그래서 REQUIRES_NEW로 커밋되는 리포지토리 메서드 호출 여부로 검증한다.
        then(pointChargeOrderRepository).should().markFailedIfPending("order-1");
        then(pointService).should(never()).charge(any(), anyInt());
    }

    @Test
    @DisplayName("confirm: 이미 처리된(PENDING이 아닌) 주문이면 재승인을 시도하지 않는다")
    void confirm_alreadyProcessedOrder_throws() {
        PointChargeOrder order = order(1L, 10000);
        order.markConfirmed();
        given(pointChargeOrderRepository.findByOrderId("order-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> pointChargeService.confirm(1L, "pay-1", "order-1", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_CHARGE_ORDER_ALREADY_PROCESSED);
        then(tossPaymentClient).should(never()).confirmPayment(anyString(), anyString(), anyLong());
    }
}
