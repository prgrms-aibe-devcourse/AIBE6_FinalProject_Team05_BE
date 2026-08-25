package com.pokade.domain.listing.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "listings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ListingGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(name = "stale_notice_sent", nullable = false)
    private boolean staleNoticeSent;

    // 판매자가 정산받을 계좌 - 실제 이체 연동 전이라 지금은 저장만 한다(주문서에서 매번 새로 입력,
    // 마이페이지 저장/재사용은 하지 않기로 함).
    @Column(name = "settlement_bank_name")
    private String settlementBankName;

    @Column(name = "settlement_account_number")
    private String settlementAccountNumber;

    @Column(name = "settlement_account_holder")
    private String settlementAccountHolder;

    // 검수 실패 등으로 카드를 판매자에게 반송해야 할 때 쓰는 주소.
    @Column(name = "return_recipient_name")
    private String returnRecipientName;

    @Column(name = "return_recipient_phone")
    private String returnRecipientPhone;

    @Column(name = "return_address")
    private String returnAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Listing(
            Long cardId,
            Long sellerId,
            Long variantId,
            Integer price,
            ListingGrade grade,
            String settlementBankName,
            String settlementAccountNumber,
            String settlementAccountHolder,
            String returnRecipientName,
            String returnRecipientPhone,
            String returnAddress
    ) {
        this.cardId = cardId;
        this.sellerId = sellerId;
        this.variantId = variantId;
        this.price = price;
        this.grade = grade;
        this.settlementBankName = settlementBankName;
        this.settlementAccountNumber = settlementAccountNumber;
        this.settlementAccountHolder = settlementAccountHolder;
        this.returnRecipientName = returnRecipientName;
        this.returnRecipientPhone = returnRecipientPhone;
        this.returnAddress = returnAddress;
        this.status = ListingStatus.ACTIVE;
        this.staleNoticeSent = false;
    }

    public void changePrice(Integer newPrice) {
        requireActive();
        this.price = newPrice;
    }

    // 가격 수정과 동일하게 ACTIVE 상태에서만 허용 - 거래가 이미 시작된 뒤 정산계좌를 바꾸면
    // 사기(엉뚱한 계좌로 정산금 가로채기) 위험이 있어 계속 막는다.
    // 각 필드는 null이면 건드리지 않고 기존 값을 유지한다(부분 수정) - "내 매물 관리"의 가격만
    // 바꾸는 빠른 수정이 이 필드들을 아예 안 보내는데, 그 요청이 기존 정산계좌/반송주소를
    // null로 지워버리면 안 되기 때문이다.
    public void updateSettlementAndReturnInfo(
            String settlementBankName,
            String settlementAccountNumber,
            String settlementAccountHolder,
            String returnRecipientName,
            String returnRecipientPhone,
            String returnAddress
    ) {
        requireActive();
        if (settlementBankName != null) this.settlementBankName = settlementBankName;
        if (settlementAccountNumber != null) this.settlementAccountNumber = settlementAccountNumber;
        if (settlementAccountHolder != null) this.settlementAccountHolder = settlementAccountHolder;
        if (returnRecipientName != null) this.returnRecipientName = returnRecipientName;
        if (returnRecipientPhone != null) this.returnRecipientPhone = returnRecipientPhone;
        if (returnAddress != null) this.returnAddress = returnAddress;
    }

    public void cancel() {
        requireActive();
        this.status = ListingStatus.CANCELLED;
    }

    public void markStaleNoticeSent() {
        this.staleNoticeSent = true;
    }

    private void requireActive() {
        if (this.status != ListingStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_LISTING_STATUS);
        }
    }
}
