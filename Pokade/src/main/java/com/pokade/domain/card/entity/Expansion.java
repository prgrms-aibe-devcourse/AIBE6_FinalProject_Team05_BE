package com.pokade.domain.card.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expansions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Expansion {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String series;

    @Column(length = 20)
    private String code;

    private Integer total;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(length = 255)
    private String logo;

    @Column(length = 255)
    private String symbol;

    @JdbcTypeCode(SqlTypes.JSON)
    private String translation;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /**
     * 과거에 translation 없이 먼저 동기화된 기존 세트를 위한 백필 전용 메서드.
     * 호출 시점에 이미 translation이 있는지 여부는 호출부(CardUpsertService)에서 판단하고,
     * 이 메서드는 값을 그대로 반영만 한다.
     */
    public void applyTranslationBackfill(String translation) {
        this.translation = translation;
    }
}