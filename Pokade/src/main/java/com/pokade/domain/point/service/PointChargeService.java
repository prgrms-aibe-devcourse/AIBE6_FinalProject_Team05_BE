package com.pokade.domain.point.service;

import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.dto.request.PointChargeReadyRequest;
import com.pokade.domain.point.dto.response.PointChargeReadyResponse;
import com.pokade.domain.point.entity.PointChargeOrder;
import com.pokade.domain.point.entity.PointChargeOrderStatus;
import com.pokade.domain.point.repository.PointChargeOrderRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointChargeService {

    private final PointChargeOrderRepository pointChargeOrderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PointService pointService;

    // 결제창을 띄우기 전에 주문을 먼저 PENDING으로 기록한다 - 승인 콜백에서 클라이언트가 보낸 금액이 아니라
    // 이 amount를 기준으로 검증/승인 요청한다.
    @Transactional
    public PointChargeReadyResponse ready(Long userId, PointChargeReadyRequest request) {
        String orderId = UUID.randomUUID().toString();
        pointChargeOrderRepository.save(PointChargeOrder.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(request.amount())
                .build());

        return new PointChargeReadyResponse(orderId, request.amount());
    }

    // 결제 승인 콜백 처리 - 성공하면 포인트를 충전하고 충전 후 잔액을 반환한다.
    @Transactional
    public int confirm(Long userId, String paymentKey, String orderId, long amount) {
        PointChargeOrder order = pointChargeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_CHARGE_ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getStatus() != PointChargeOrderStatus.PENDING) {
            // 이미 처리된 주문이면 토스 승인 API를 다시 호출하지 않고 즉시 거부한다 (중복 콜백 방어).
            throw new BusinessException(ErrorCode.POINT_CHARGE_ORDER_ALREADY_PROCESSED);
        }
        if (order.getAmount() != amount) {
            // 리다이렉트로 전달된 금액이 최초 요청 금액과 다르면 위변조 의심 - 토스 승인 API를 호출하지 않고 거부한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 금액이 일치하지 않습니다.");
        }

        try {
            tossPaymentClient.confirmPayment(paymentKey, orderId, order.getAmount());
        } catch (BusinessException e) {
            // 이 메서드는 결국 예외를 다시 던져 전체 트랜잭션이 롤백되므로, 실패 기록은 별도 빈(리포지토리)의
            // REQUIRES_NEW 트랜잭션으로 즉시 커밋해야 한다 - 같은 트랜잭션 안에서 엔티티만 바꾸면 유실된다.
            pointChargeOrderRepository.markFailedIfPending(orderId);
            throw e;
        }

        order.markConfirmed();
        return pointService.charge(userId, order.getAmount());
    }
}
