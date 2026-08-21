package com.pokade.domain.price.entity;

import com.pokade.domain.listing.entity.ListingGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "buy_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuyOffer {

    // 30일 고정 유효기간 - 매물 등록(Listing)엔 만료 개념이 없지만 구매입찰은 스키마상
    // expires_at이 있고, 매수자가 계속 유지할 생각이 없는 옛 입찰이 무기한 쌓이지 않게 하기 위함.
    private static final int EXPIRES_IN_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "variant_id")
    private Long variantId;

    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ListingGrade grade;

    private String status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "recipient_address")
    private String recipientAddress;

    @Column(name = "toss_payment_key")
    private String tossPaymentKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // id는 실제 생성 흐름(BuyOfferOrderService.confirmPurchase)에서는 절대 넘기지 않는다(IDENTITY가
    // 채움) - 테스트에서 특정 id를 가진 픽스처가 필요할 때만 쓴다(Listing.builder()와 동일한 성격).
    @Builder
    public BuyOffer(
            Long id,
            Long cardId,
            Long buyerId,
            Long variantId,
            Integer price,
            ListingGrade grade,
            String recipientName,
            String recipientPhone,
            String recipientAddress,
            String tossPaymentKey
    ) {
        this.id = id;
        this.cardId = cardId;
        this.buyerId = buyerId;
        this.variantId = variantId;
        this.price = price;
        this.grade = grade;
        this.status = "ACTIVE";
        this.expiresAt = LocalDateTime.now().plusDays(EXPIRES_IN_DAYS);
        this.priceUpdatedAt = LocalDateTime.now();
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
        this.tossPaymentKey = tossPaymentKey;
    }
}
