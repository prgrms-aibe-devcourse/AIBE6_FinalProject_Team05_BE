package com.pokade.domain.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;

@Entity
@Table(name = "watchlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    // null이면 대표 변형 기준
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "target_buy_price")
    private Integer targetBuyPrice;

    @Column(name = "target_sell_price")
    private Integer targetSellPrice;

    @Column(name = "is_notified", nullable = false)
    private boolean isNotified;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Watchlist(Long userId, Long cardId, Long variantId, Integer targetBuyPrice, Integer targetSellPrice) {
        this.userId = userId;
        this.cardId = cardId;
        this.variantId = variantId;
        this.targetBuyPrice = targetBuyPrice;
        this.targetSellPrice = targetSellPrice;
        this.isNotified = false;
    }

    public void markAsNotified() {
        this.isNotified = true;
    }

    // updateTargetPrices()의 "가격이 실제로 바뀐 경우"에만 리셋하는 조건부 로직과 달리,
    // 조건 없이 무조건 리셋한다 - 내부적으로 같은 resetNotification()을 공유
    public void requestNotificationAgain() {
        resetNotification();
    }

    private void resetNotification() {
        this.isNotified = false;
    }

    // null로 온 필드는 "값을 지운다"가 아니라 "기존 값 유지"로 해석한다 - 부분 업데이트를 지원하기
    // 위함. 두 필드를 한꺼번에 지우는 기능은 없음(전체 삭제는 DELETE로만 가능).
    public void updateTargetPrices(Integer targetBuyPrice, Integer targetSellPrice) {
        Integer resolvedBuyPrice = targetBuyPrice != null ? targetBuyPrice : this.targetBuyPrice;
        Integer resolvedSellPrice = targetSellPrice != null ? targetSellPrice : this.targetSellPrice;
        // 목표가가 실제로 바뀐 경우에만 isNotified를 리셋한다 - 이미 알림이 간 목표가를 그대로 재저장하는
        // no-op 수정에서는 배치가 불필요하게 재알림을 보내지 않도록 한다.
        boolean changed = !Objects.equals(this.targetBuyPrice, resolvedBuyPrice)
                || !Objects.equals(this.targetSellPrice, resolvedSellPrice);
        this.targetBuyPrice = resolvedBuyPrice;
        this.targetSellPrice = resolvedSellPrice;
        if (changed) {
            resetNotification();
        }
    }
}
