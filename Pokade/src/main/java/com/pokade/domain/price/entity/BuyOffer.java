package com.pokade.domain.price.entity;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
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

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ListingGrade grade;

    private String status;

    // 아직 구매입찰 등록 API가 없어(테이블은 매수자가 직접 만들지 못하고 시드/향후 배치로만 채워짐)
    // 오직 테스트 픽스처 구성용으로만 쓰인다 - Listing.builder()와 동일한 성격.
    @Builder
    public BuyOffer(Long id, Long cardId, Long variantId, Integer price, ListingGrade grade, String status) {
        this.id = id;
        this.cardId = cardId;
        this.variantId = variantId;
        this.price = price;
        this.grade = grade;
        this.status = status;
    }
}
