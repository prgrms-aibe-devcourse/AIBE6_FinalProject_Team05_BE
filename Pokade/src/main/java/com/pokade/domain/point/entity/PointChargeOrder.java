package com.pokade.domain.point.entity;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 포인트 충전 주문 - 토스페이먼츠 결제창을 띄우기 전에 PENDING으로 먼저 만들어 두고, 승인 콜백에서
// 이 행의 amount를 기준으로 검증한다(클라이언트가 리다이렉트로 보내는 amount를 그대로 믿지 않기 위함).
@Entity
@Table(name = "point_charge_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointChargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointChargeOrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointChargeOrder(String orderId, Long userId, Integer amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = PointChargeOrderStatus.PENDING;
    }

    // 승인 완료 처리 - PENDING 상태가 아니면(중복 콜백 등) 거부해 중복 충전을 막는다.
    // 실패 기록(FAILED)은 이 엔티티 메서드가 아니라 PointChargeOrderRepository.markFailedIfPending()로
    // 처리한다 - 실패는 항상 예외가 다시 던져지는 트랜잭션 안에서 기록되므로, 같은 트랜잭션 안에서
    // 엔티티 필드만 바꾸면 롤백과 함께 유실된다(REQUIRES_NEW로 별도 커밋 필요).
    public void markConfirmed() {
        if (this.status != PointChargeOrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.POINT_CHARGE_ORDER_ALREADY_PROCESSED);
        }
        this.status = PointChargeOrderStatus.CONFIRMED;
    }
}
