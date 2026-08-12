package com.pokade.domain.card.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CardVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "variant_name", nullable = false, length = 100)
    private String variantName;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "image_small", length = 255)
    private String imageSmall;

    @Column(name = "image_large", length = 255)
    private String imageLarge;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /**
     * Scrydex 동기화 배치에서 대표 판본이 바뀌었을 때도 기존 row를 delete+insert하지 않고 이 메서드로
     * variant_name/이미지만 새 값으로 바꿔치기한다 - 내부 PK(id)가 유지되어야 listings 등 이 id를 참조하는
     * 다른 테이블의 FK가 깨지지 않는다.
     */
    public void applySync(String variantName, String imageSmall, String imageLarge, LocalDateTime syncedAt) {
        this.variantName = variantName;
        this.imageSmall = imageSmall;
        this.imageLarge = imageLarge;
        this.syncedAt = syncedAt;
    }
}