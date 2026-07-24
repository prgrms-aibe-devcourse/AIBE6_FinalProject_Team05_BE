package com.pokade.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardVariant {

    @Id
    private Long id;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "is_primary")
    private Boolean isPrimary;
}
