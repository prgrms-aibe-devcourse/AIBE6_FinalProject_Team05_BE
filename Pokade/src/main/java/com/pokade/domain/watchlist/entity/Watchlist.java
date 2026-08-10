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
}
