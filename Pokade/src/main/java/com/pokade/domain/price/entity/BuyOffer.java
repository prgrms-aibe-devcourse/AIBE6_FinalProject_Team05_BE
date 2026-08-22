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
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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

    // 이 구매입찰 결제 시 포인트로 미리 차감된 금액 - 즉시판매로 이 입찰에 매칭된 거래가 나중에
    // 취소되면 PointService.refund()로 이만큼 되돌려줘야 한다(Payment.pointsUsed와 동일한 이유).
    @Column(name = "points_used")
    private Integer pointsUsed;

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
            String tossPaymentKey,
            Integer pointsUsed
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
        this.pointsUsed = pointsUsed != null ? pointsUsed : 0;
    }

    // 판매자가 즉시판매로 이 구매입찰에 매칭했을 때 호출 - ACTIVE가 아니거나(이미 체결) 만료됐으면
    // 거부한다. 이후 orderbook/최고가 조회 쿼리가 전부 status='ACTIVE'만 보므로 자연히 목록에서 빠진다.
    public void markMatched() {
        if (!"ACTIVE".equals(this.status) || this.expiresAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUY_OFFER_ALREADY_MATCHED);
        }
        this.status = "MATCHED";
    }
}
