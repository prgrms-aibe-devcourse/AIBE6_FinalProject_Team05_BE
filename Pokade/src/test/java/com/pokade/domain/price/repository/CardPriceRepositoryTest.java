package com.pokade.domain.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.price.entity.CardPrice;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class CardPriceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CardPriceRepository cardPriceRepository;

    @Autowired
    private EntityManager entityManager;

    private Long persistCardPrice(String cardName, BigDecimal change7dPct) {
        Card card = Card.builder().name(cardName).build();
        entityManager.persist(card);

        CardVariant variant = CardVariant.builder()
                .card(card)
                .variantName("holofoil")
                .primary(true)
                .syncedAt(LocalDateTime.now())
                .build();
        entityManager.persist(variant);

        CardPrice cardPrice = CardPrice.builder()
                .variant(variant)
                .priceType("graded")
                .grade("10")
                .company("PSA")
                .market(new BigDecimal("100.00"))
                .currency("USD")
                .change7dPct(change7dPct)
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(cardPrice);

        return card.getId();
    }

    @Test
    @DisplayName("t1 변동률 내림차순으로 정렬해 상위 N건을 반환한다")
    void t1() {
        persistCardPrice("A", new BigDecimal("1.00"));
        persistCardPrice("B", new BigDecimal("8.47"));
        persistCardPrice("C", new BigDecimal("-5.63"));
        entityManager.flush();
        entityManager.clear();

        List<CardPrice> result = cardPriceRepository.findTopRising(PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChange7dPct()).isEqualByComparingTo("8.47");
        assertThat(result.get(1).getChange7dPct()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("t2 변동률 오름차순(가장 많이 하락한 순)으로 정렬해 상위 N건을 반환한다")
    void t2() {
        persistCardPrice("A", new BigDecimal("1.00"));
        persistCardPrice("B", new BigDecimal("8.47"));
        persistCardPrice("C", new BigDecimal("-5.63"));
        entityManager.flush();
        entityManager.clear();

        List<CardPrice> result = cardPriceRepository.findTopFalling(PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChange7dPct()).isEqualByComparingTo("-5.63");
        assertThat(result.get(1).getChange7dPct()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("t3 change_7d_pct가 NULL인 행(동기화 배치 미실행)은 결과에서 제외된다")
    void t3() {
        persistCardPrice("A", null);
        entityManager.flush();
        entityManager.clear();

        List<CardPrice> risingResult = cardPriceRepository.findTopRising(PageRequest.of(0, 10));
        List<CardPrice> fallingResult = cardPriceRepository.findTopFalling(PageRequest.of(0, 10));

        assertThat(risingResult).isEmpty();
        assertThat(fallingResult).isEmpty();
    }

    @Test
    @DisplayName("t4 조회 결과의 variant와 card는 JOIN FETCH로 함께 로딩된다")
    void t4() {
        persistCardPrice("Charizard", new BigDecimal("3.63"));
        entityManager.flush();
        entityManager.clear();

        List<CardPrice> result = cardPriceRepository.findTopRising(PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVariant().getCard().getName()).isEqualTo("Charizard");
    }
}
