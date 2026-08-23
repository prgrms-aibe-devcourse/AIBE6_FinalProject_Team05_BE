package com.pokade.domain.price.entity;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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

// 구매입찰 등록 주문 - TradeOrder/PointChargeOrder와 동일한 "결제창을 띄우기 전 PENDING으로 먼저
// 기록" 패턴. 구매입찰은 매물처럼 "잠글" 기존 리소스가 없으므로(그냥 새로 만드는 입찰), confirm 시
// 매물 잠금에 해당하는 경쟁 상태 처리는 필요 없다 - 결제 승인만 되면 바로 BuyOffer를 생성한다.
@Entity
@Table(name = "buy_offer_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuyOfferOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "variant_id")
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ListingGrade grade;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "shipping_fee", nullable = false)
    private Integer shippingFee;

    // 상품가+배송비 중 결제 전에 미리 차감하기로 한 포인트 액수 - 0이면 포인트 미사용.
    @Column(name = "points_used", nullable = false)
    private Integer pointsUsed;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "recipient_address", nullable = false)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuyOfferOrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public BuyOfferOrder(
            String orderId,
            Long buyerId,
            Long cardId,
            Long variantId,
            ListingGrade grade,
            Integer price,
            Integer shippingFee,
            Integer pointsUsed,
            String recipientName,
            String recipientPhone,
            String recipientAddress
    ) {
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.cardId = cardId;
        this.variantId = variantId;
        this.grade = grade;
        this.price = price;
        this.shippingFee = shippingFee;
        this.pointsUsed = pointsUsed != null ? pointsUsed : 0;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
        this.status = BuyOfferOrderStatus.PENDING;
    }

    public Integer getTotalAmount() {
        return this.price + this.shippingFee;
    }

    // 실제 토스로 결제해야 하는 금액 - 상품가+배송비에서 포인트 사용액을 뺀 나머지. 포인트로 전액을
    // 충당했으면 0이 되고, 그 경우 결제 승인 단계에서 토스 호출 자체를 건너뛴다.
    public Integer getPaymentAmount() {
        return getTotalAmount() - this.pointsUsed;
    }

    // 승인 완료 처리 - PENDING 상태가 아니면(중복 콜백 등) 거부해 중복 생성을 막는다. 실패 기록은
    // BuyOfferOrderRepository.markFailedIfPending()로 REQUIRES_NEW 커밋 처리한다(TradeOrder와 동일한 이유).
    public void markConfirmed() {
        if (this.status != BuyOfferOrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUY_OFFER_ORDER_ALREADY_PROCESSED);
        }
        this.status = BuyOfferOrderStatus.CONFIRMED;
    }
}
