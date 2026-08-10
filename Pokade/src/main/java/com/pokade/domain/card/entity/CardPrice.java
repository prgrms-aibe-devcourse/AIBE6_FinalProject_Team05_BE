package com.pokade.domain.card.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "card_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CardPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private CardVariant variant;

    @Column(name = "price_type", nullable = false, length = 10)
    private String priceType;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String grade = "";

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String company = "";

    private BigDecimal low;
    private BigDecimal mid;
    private BigDecimal high;
    private BigDecimal market;

    @Column(length = 10)
    private String currency;

    @Column(name = "change_1d_pct")
    private BigDecimal change1dPct;

    @Column(name = "change_7d_pct")
    private BigDecimal change7dPct;

    @Column(name = "change_14d_pct")
    private BigDecimal change14dPct;

    @Column(name = "change_30d_pct")
    private BigDecimal change30dPct;

    @Column(name = "change_90d_pct")
    private BigDecimal change90dPct;

    @Column(name = "change_180d_pct")
    private BigDecimal change180dPct;

    @Column(name = "change_7d_amount")
    private BigDecimal change7dAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Scrydex 동기화 배치에서 raw NM 가격만 갱신한다. change_* 트렌드 컬럼은 API가 제공하지 않아
     * 이 배치에서는 건드리지 않는다(추후 목업/자체 계산으로 채워질 예정).
     */
    public void applySync(BigDecimal low, BigDecimal mid, BigDecimal high, BigDecimal market,
                           String currency, LocalDateTime updatedAt) {
        this.low = low;
        this.mid = mid;
        this.high = high;
        this.market = market;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }
}
