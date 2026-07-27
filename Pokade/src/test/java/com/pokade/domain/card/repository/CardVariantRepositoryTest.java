package com.pokade.domain.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class CardVariantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CardVariantRepository cardVariantRepository;

    @Autowired
    private EntityManager entityManager;

    private Card multiVariantCard;

    @BeforeEach
    void setUp() {
        Expansion base1 = Expansion.builder()
                .id("base1")
                .name("Base")
                .syncedAt(LocalDateTime.now())
                .build();
        entityManager.persist(base1);

        multiVariantCard = Card.builder()
                .name("Charizard")
                .rarity("Rare Holo")
                .expansion(base1)
                .build();
        entityManager.persist(multiVariantCard);

        persistVariant(multiVariantCard, "firstEditionHolofoil", false);
        persistVariant(multiVariantCard, "unlimitedHolofoil", true);
        persistVariant(multiVariantCard, "reverseHolofoil", false);
    }

    private void persistVariant(Card card, String variantName, boolean primary) {
        CardVariant variant = CardVariant.builder()
                .card(card)
                .variantName(variantName)
                .primary(primary)
                .syncedAt(LocalDateTime.now())
                .build();
        entityManager.persist(variant);
    }

    @Test
    @DisplayName("t1 카드에 변형이 여러 개 있으면 대표 변형이 먼저, 나머지는 변형명 오름차순으로 조회된다")
    void t1() {
        List<CardVariant> result = cardVariantRepository
                .findByCardIdOrderByPrimaryDescVariantNameAsc(multiVariantCard.getId());

        assertThat(result)
                .extracting(CardVariant::getVariantName)
                .containsExactly("unlimitedHolofoil", "firstEditionHolofoil", "reverseHolofoil");
    }

    @Test
    @DisplayName("t2 변형이 없는 카드는 빈 목록을 반환한다")
    void t2() {
        Card noVariantCard = Card.builder().name("Pikachu").build();
        entityManager.persist(noVariantCard);

        List<CardVariant> result = cardVariantRepository
                .findByCardIdOrderByPrimaryDescVariantNameAsc(noVariantCard.getId());

        assertThat(result).isEmpty();
    }
}
