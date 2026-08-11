package com.pokade.domain.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardPrice;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class PriceCardStatsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PriceCardStatsRepository priceCardStatsRepository;

    @Autowired
    private EntityManager entityManager;

    private Long variantId;

    @BeforeEach
    void setUp() {
        Card card = Card.builder().name("Charizard").build();
        entityManager.persist(card);

        CardVariant variant = CardVariant.builder()
                .card(card)
                .variantName("unlimitedHolofoil")
                .primary(true)
                .syncedAt(LocalDateTime.now())
                .build();
        entityManager.persist(variant);
        variantId = variant.getId();

        CardPrice psa10 = CardPrice.builder()
                .variant(variant)
                .priceType("graded")
                .grade("10")
                .company("PSA")
                .market(new BigDecimal("2566.00"))
                .currency("USD")
                .change1dPct(new BigDecimal("1.10"))
                .change7dPct(new BigDecimal("4.55"))
                .change14dPct(new BigDecimal("-2.10"))
                .change30dPct(new BigDecimal("-5.63"))
                .change90dPct(new BigDecimal("11.30"))
                .change180dPct(new BigDecimal("-8.90"))
                .change7dAmount(new BigDecimal("116.75"))
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(psa10);

        CardPrice sGrade = CardPrice.builder()
                .variant(variant)
                .priceType("graded")
                .grade("S")
                .company("")
                .market(new BigDecimal("1539.61"))
                .currency("USD")
                .change7dPct(new BigDecimal("-2.71"))
                .change7dAmount(new BigDecimal("-4.86"))
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(sGrade);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("t1 period 값에 따라 대응하는 change_*_pct 컬럼을 반환한다")
    void t1() {
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "1d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("1.10");
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "7d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("4.55");
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "14d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("-2.10");
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "30d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("-5.63");
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "90d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("11.30");
        assertThat(priceCardStatsRepository.findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "180d")
                .orElseThrow().getChangePct()).isEqualByComparingTo("-8.90");
    }

    @Test
    @DisplayName("t2 7일치 등락 금액(change_7d_amount)도 함께 반환한다")
    void t2() {
        PriceCardStatsRepository.CardPriceChangeView view = priceCardStatsRepository
                .findChangeByVariantGradeCompanyAndPeriod(variantId, "10", "PSA", "7d")
                .orElseThrow();

        assertThat(view.getChange7dAmount()).isEqualByComparingTo("116.75");
    }

    @Test
    @DisplayName("t3 company가 다른 등급(PSA 공인 등급 vs 자체 S등급)은 서로 구분되어 조회된다")
    void t3() {
        PriceCardStatsRepository.CardPriceChangeView sView = priceCardStatsRepository
                .findChangeByVariantGradeCompanyAndPeriod(variantId, "S", "", "7d")
                .orElseThrow();

        assertThat(sView.getChangePct()).isEqualByComparingTo("-2.71");
        assertThat(sView.getChange7dAmount()).isEqualByComparingTo("-4.86");
    }

    @Test
    @DisplayName("t4 variant/grade/company 조합에 해당하는 행이 없으면 빈 Optional을 반환한다")
    void t4() {
        Optional<PriceCardStatsRepository.CardPriceChangeView> result = priceCardStatsRepository
                .findChangeByVariantGradeCompanyAndPeriod(variantId, "A", "", "7d");

        assertThat(result).isEmpty();
    }
}
