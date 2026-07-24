package com.pokade.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.pokade.card.entity.Card;
import com.pokade.card.entity.Expansion;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class CardRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        Expansion base1 = persistExpansion("base1", "Base");
        Expansion sv3pt5 = persistExpansion("sv3pt5", "151");

        persistCard("Charizard", "Rare Holo", base1, "Fire");
        persistCard("Blastoise", "Rare Holo", base1, "Water");
        persistCard("Pikachu", "Common", base1, "Lightning");
        persistCard("Charizard ex", "Double Rare", sv3pt5, "Fire");
    }

    private Expansion persistExpansion(String id, String name) {
        Expansion expansion = Expansion.builder()
                .id(id)
                .name(name)
                .syncedAt(LocalDateTime.now())
                .build();
        entityManager.persist(expansion);
        return expansion;
    }

    private void persistCard(String name, String rarity, Expansion expansion, String type) {
        Card card = Card.builder()
                .name(name)
                .rarity(rarity)
                .expansion(expansion)
                .types(List.of(type))
                .build();
        entityManager.persist(card);
    }

    @Test
    @DisplayName("t1 이름에 검색어가 포함된 카드를 부분 일치로 조회한다")
    void t1() {
        Page<Card> result = cardRepository.search("char", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t2 types 배열에 검색 타입이 포함된 카드만 조회한다")
    void t2() {
        Page<Card> result = cardRepository.search(null, "Fire", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t3 rarity가 정확히 일치하는 카드만 조회한다")
    void t3() {
        Page<Card> result = cardRepository.search(null, null, "Common", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Pikachu");
    }

    @Test
    @DisplayName("t4 expansionId가 정확히 일치하는 카드만 조회한다")
    void t4() {
        Page<Card> result = cardRepository.search(null, null, null, "sv3pt5", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t5 여러 조건을 조합하면 AND로 필터링된다")
    void t5() {
        Page<Card> result = cardRepository.search("char", "Fire", null, "base1", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t6 조건이 없으면 전체 카드를 페이지 크기만큼 반환한다")
    void t6() {
        Page<Card> result = cardRepository.search(null, null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }
}