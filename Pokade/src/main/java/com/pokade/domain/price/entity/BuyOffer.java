package com.pokade.domain.price.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buy_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuyOffer {

    @Id
    private Long id;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "variant_id")
    private Long variantId;

    private Integer price;

    private String status;
}
