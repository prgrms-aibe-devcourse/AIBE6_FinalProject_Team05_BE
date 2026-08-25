package com.pokade.domain.portfolio.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    // NULL이면 대표 변형 기준으로 시세 계산
    @Column(name = "variant_id")
    private Long variantId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "acquired_price")
    private Integer acquiredPrice;

    @Column(name = "acquired_at")
    private LocalDateTime acquiredAt;

    // 거래를 통해 취득한 경우 연결 (UNIQUE 제약)
    @Column(name = "trade_id", unique = true)
    private Long tradeId;

    // AI 등급 진단 결과로부터 등록된 경우 연결 (UNIQUE 제약) — 동일 진단 결과의 중복 등록 방지용.
    @Column(name = "grade_result_id", unique = true)
    private Long gradeResultId;

    // 커스텀 표지 이미지의 S3 key. NULL이면 카드 기본 이미지를 그대로 쓴다.
    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Builder
    public PortfolioItem(Long userId, Long cardId, Long variantId, Integer quantity,
                         Integer acquiredPrice, LocalDateTime acquiredAt, Long tradeId,
                         Long gradeResultId, String thumbnailKey) {
        this.userId = userId;
        this.cardId = cardId;
        this.variantId = variantId;
        this.quantity = quantity != null ? quantity : 1;
        this.acquiredPrice = acquiredPrice;
        this.acquiredAt = acquiredAt;
        this.tradeId = tradeId;
        this.gradeResultId = gradeResultId;
        this.thumbnailKey = thumbnailKey;
    }

    // 도감에서 사용자가 표지 사진을 직접 바꿀 때 사용 — AI 진단 등록 여부와 무관하게 언제든 교체 가능.
    public void changeThumbnail(String newKey) {
        this.thumbnailKey = newKey;
    }

    public void update(Integer quantity, Integer acquiredPrice, LocalDateTime acquiredAt) {
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (acquiredPrice != null) {
            this.acquiredPrice = acquiredPrice;
        }
        if (acquiredAt != null) {
            this.acquiredAt = acquiredAt;
        }
    }
}
