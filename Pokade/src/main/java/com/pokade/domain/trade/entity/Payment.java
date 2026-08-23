package com.pokade.domain.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false, unique = true)
    private Trade trade;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Integer amount;

    // 이 결제 중 포인트로 미리 차감된 금액 - 0이면 포인트 미사용. 거래 취소 시 이 값만큼
    // PointService.refund()로 되돌려준다(전액 포인트 결제라 tossPaymentKey가 없는 경우 포함).
    @Column(name = "points_used")
    private Integer pointsUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // 토스페이먼츠 결제 승인 시 발급되는 키 - 거래 취소 시 이 키로 Toss 결제취소(환불) API를 호출한다.
    @Column(name = "toss_payment_key", length = 200)
    private String tossPaymentKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Payment(Trade trade, Long buyerId, Integer amount, Integer pointsUsed,
                   PaymentMethod method, String tossPaymentKey) {
        this.trade = trade;
        this.buyerId = buyerId;
        this.amount = amount;
        this.pointsUsed = pointsUsed != null ? pointsUsed : 0;
        this.method = method;
        this.tossPaymentKey = tossPaymentKey;
        this.status = PaymentStatus.ESCROW_HELD;
    }

    public void settle() {
        this.status = PaymentStatus.SETTLED;
        this.paidAt = LocalDateTime.now();
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }
}
