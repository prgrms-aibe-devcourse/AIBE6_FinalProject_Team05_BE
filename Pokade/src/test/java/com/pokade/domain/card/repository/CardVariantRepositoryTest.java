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

    private Long persistSeller(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) "
                                + "VALUES (:email, 'tester', 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .getSingleResult()).longValue();
    }

    private Long persistListing(Long cardId, Long sellerId, Long variantId, String grade, String status) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status) "
                                + "VALUES (:cardId, :sellerId, :variantId, 10000, :grade, :status) RETURNING id")
                .setParameter("cardId", cardId)
                .setParameter("sellerId", sellerId)
                .setParameter("variantId", variantId)
                .setParameter("grade", grade)
                .setParameter("status", status)
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("t3 variant_id가 채워진 매물은 해당 variant의 등급으로 매핑된다")
    void t3() {
        Long seller = persistSeller("variant-grade-seller@test.com");
        CardVariant firstEdition = cardVariantRepository
                .findByCardIdOrderByPrimaryDescVariantNameAsc(multiVariantCard.getId())
                .stream().filter(v -> v.getVariantName().equals("firstEditionHolofoil")).findFirst().orElseThrow();
        persistListing(multiVariantCard.getId(), seller, firstEdition.getId(), "A", "ACTIVE");
        entityManager.flush();

        List<CardVariantRepository.VariantGradeView> result = cardVariantRepository.findGradesByCardId(multiVariantCard.getId());

        assertThat(result)
                .extracting(CardVariantRepository.VariantGradeView::getVariantId, CardVariantRepository.VariantGradeView::getGrade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(firstEdition.getId(), "A"));
    }

    @Test
    @DisplayName("t4 variant_id가 NULL인 매물은 대표 변형(primary) 등급으로 합산된다")
    void t4() {
        Long seller = persistSeller("variant-grade-null-seller@test.com");
        CardVariant primary = cardVariantRepository
                .findByCardIdOrderByPrimaryDescVariantNameAsc(multiVariantCard.getId())
                .stream().filter(CardVariant::isPrimary).findFirst().orElseThrow();
        persistListing(multiVariantCard.getId(), seller, null, "S", "ACTIVE");
        entityManager.flush();

        List<CardVariantRepository.VariantGradeView> result = cardVariantRepository.findGradesByCardId(multiVariantCard.getId());

        assertThat(result)
                .extracting(CardVariantRepository.VariantGradeView::getVariantId, CardVariantRepository.VariantGradeView::getGrade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(primary.getId(), "S"));
    }

    @Test
    @DisplayName("t5 매물이 없는 카드는 등급 조회 결과가 빈 목록이다")
    void t5() {
        List<CardVariantRepository.VariantGradeView> result = cardVariantRepository.findGradesByCardId(multiVariantCard.getId());

        assertThat(result).isEmpty();
    }
}
