package com.pokade.domain.trade;

import com.pokade.domain.listing.Listing;
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

    private static final Set<TradeStatus> CANCELLABLE_STATUSES =
            Set.of(TradeStatus.PENDING, TradeStatus.MATCHED);

    private static final Set<TradeStatus> FINAL_STATUSES =
            Set.of(TradeStatus.COMPLETED, TradeStatus.CANCELLED);

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

    public void complete() {
        if (FINAL_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "이미 확정되었거나 취소된 거래입니다.");
        }
        this.status = TradeStatus.COMPLETED;
        this.confirmedAt = LocalDateTime.now();
        this.settledAt = LocalDateTime.now();
    }

    public void cancel() {
        if (!CANCELLABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.INVALID_TRADE_STATUS, "체결 확정 전 단계의 거래만 취소할 수 있습니다.");
        }
        this.status = TradeStatus.CANCELLED;
    }
}
