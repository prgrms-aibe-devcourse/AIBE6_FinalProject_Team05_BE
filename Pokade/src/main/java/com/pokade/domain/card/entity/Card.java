package com.pokade.domain.card.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 50, unique = true)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "set_name", length = 100)
    private String setName;

    @Column(length = 100)
    private String rarity;

    @Column(length = 50)
    private String supertype;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> subtypes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> types;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evolves_from", columnDefinition = "text[]")
    private List<String> evolvesFrom;

    @Column(name = "printed_number", length = 50)
    private String printedNumber;

    @Column(name = "rarity_code", length = 20)
    private String rarityCode;

    @Column(length = 200)
    private String artist;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "national_pokedex_numbers", columnDefinition = "integer[]")
    private List<Integer> nationalPokedexNumbers;

    @Column(name = "image_small", length = 255)
    private String imageSmall;

    @Column(name = "image_medium", length = 255)
    private String imageMedium;

    @Column(name = "image_large", length = 255)
    private String imageLarge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expansion_id")
    private Expansion expansion;

    @Column(name = "expansion_sort_order")
    private Integer expansionSortOrder;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}