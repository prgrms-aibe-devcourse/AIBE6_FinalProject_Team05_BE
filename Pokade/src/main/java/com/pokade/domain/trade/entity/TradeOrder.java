package com.pokade.domain.trade.entity;

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

// 매물 즉시구매 주문 - 토스페이먼츠 결제창을 띄우기 전, 매물을 잠그지 않은 채로 PENDING 기록만 먼저
// 남긴다(결제를 포기해도 매물이 영구히 TRADING 상태로 묶이지 않도록). 실제 매물 잠금(markAsTrading)은
// 결제 승인 이후 TradeService.confirmPurchase()에서 수행한다.
@Entity
@Table(name = "trade_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeOrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TradeOrder(String orderId, Long buyerId, Long listingId, Integer amount) {
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.listingId = listingId;
        this.amount = amount;
        this.status = TradeOrderStatus.PENDING;
    }

    // 승인 완료 처리 - PENDING 상태가 아니면(중복 콜백 등) 거부해 중복 구매 처리를 막는다.
    // 실패 기록은 이 메서드가 아니라 TradeOrderRepository.markFailedIfPending()로 처리한다 - 실패는
    // 항상 예외가 다시 던져지는 트랜잭션 안에서 기록되므로, 같은 트랜잭션 안에서 엔티티 필드만 바꾸면
    // 롤백과 함께 유실된다(REQUIRES_NEW로 별도 커밋 필요).
    public void markConfirmed() {
        if (this.status != TradeOrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRADE_ORDER_ALREADY_PROCESSED);
        }
        this.status = TradeOrderStatus.CONFIRMED;
    }
}
