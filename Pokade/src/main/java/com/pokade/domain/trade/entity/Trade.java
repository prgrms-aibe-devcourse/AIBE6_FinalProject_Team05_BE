package com.pokade.domain.trade.entity;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

    // DELIVERED 이후는 구매자가 실물을 이미 수령한 상태라 취소를 막는다 - 결제가 실제 토스 에스크로로
    // 잡혀있는 지금은, 여기서 취소를 허용하면 카드를 받고도 결제를 환불받아가는 경로가 생긴다.
    private static final Set<TradeStatus> NOT_CANCELLABLE_STATUSES =
            Set.of(TradeStatus.DELIVERED, TradeStatus.COMPLETED, TradeStatus.CANCELLED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Trade(Listing listing, Long buyerId, Integer price) {
        this.listing = listing;
        this.buyerId = buyerId;
        this.price = price;
        this.status = TradeStatus.PENDING;
    }

    // 판매자가 플랫폼으로 발송했음을 기록 (판매자 액션)
    public void shipToPlatform() {
        if (this.status != TradeStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "발송 대기 상태의 거래만 발송 처리할 수 있습니다.");
        }
        this.status = TradeStatus.SHIPPED_TO_PLATFORM;
        this.shippedAt = LocalDateTime.now();
    }

    // 플랫폼 검수 완료 (관리자 액션)
    public void markInspected() {
        if (this.status != TradeStatus.SHIPPED_TO_PLATFORM) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "발송된 거래만 검수 처리할 수 있습니다.");
        }
        this.status = TradeStatus.INSPECTED;
        this.inspectedAt = LocalDateTime.now();
    }

    // 구매자에게 배송 완료 (관리자 액션)
    public void markDelivered() {
        if (this.status != TradeStatus.INSPECTED) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "검수 완료된 거래만 배송 처리할 수 있습니다.");
        }
        this.status = TradeStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    // 배송 완료된 거래만 구매자가 확정할 수 있다 (발송 전 조기 확정 방지)
    public void complete() {
        if (this.status != TradeStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "배송 완료된 거래만 구매를 확정할 수 있습니다.");
        }
        this.status = TradeStatus.COMPLETED;
        this.confirmedAt = LocalDateTime.now();
        this.settledAt = LocalDateTime.now();
    }

    public void cancel() {
        if (NOT_CANCELLABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "배송 완료 이후에는 취소할 수 없습니다.");
        }
        this.status = TradeStatus.CANCELLED;
    }
}
