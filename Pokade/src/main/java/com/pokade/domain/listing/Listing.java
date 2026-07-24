package com.pokade.domain.listing;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Listing(Long cardId, Long sellerId, Long variantId, Integer price, ListingGrade grade) {
        this.cardId = cardId;
        this.sellerId = sellerId;
        this.variantId = variantId;
        this.price = price;
        this.grade = grade;
        this.status = ListingStatus.ACTIVE;
        this.staleNoticeSent = false;
    }

    public void changePrice(Integer newPrice) {
        requireActive();
        this.price = newPrice;
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
