package com.pokade.domain.card.repository;

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

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class CardRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private EntityManager entityManager;

    private Card charizard;
    private Card charizardEx;
    private Card professorsResearch;
    private Card quickBall;

    @BeforeEach
    void setUp() {
        Expansion base1 = persistExpansion("base1", "Base");
        Expansion sv3pt5 = persistExpansion("sv3pt5", "151");
        Expansion swsh1 = persistExpansion("swsh1", "Sword & Shield");

        charizard = persistCard("Charizard", "Rare Holo", base1, "Fire", List.of(6));
        persistCard("Blastoise", "Rare Holo", base1, "Water", List.of(9));
        persistCard("Pikachu", "Common", base1, "Lightning", List.of(25));
        charizardEx = persistCard("Charizard ex", "Double Rare", sv3pt5, "Fire", List.of(6));
        professorsResearch = persistTrainerCard("Professor's Research", base1);
        quickBall = persistTrainerCard("Quick Ball", swsh1);
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

    private Card persistCard(String name, String rarity, Expansion expansion, String type, List<Integer> pokedexNumbers) {
        Card card = Card.builder()
                .name(name)
                .rarity(rarity)
                .supertype("Pokémon")
                .expansion(expansion)
                .types(type != null ? List.of(type) : null)
                .nationalPokedexNumbers(pokedexNumbers)
                .build();
        entityManager.persist(card);
        return card;
    }

    private Card persistTrainerCard(String name, Expansion expansion) {
        Card card = Card.builder()
                .name(name)
                .supertype("Trainer")
                .expansion(expansion)
                .build();
        entityManager.persist(card);
        return card;
    }

    @Test
    @DisplayName("t1 이름 키워드에 부분 일치하는 카드를 대소문자 구분 없이 조회한다")
    void t1() {
        Page<Card> result = cardRepository.findByNameContainingIgnoreCase("CHAR", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t2 types 배열에 검색 타입이 포함된 카드만 조회한다")
    void t2() {
        Page<Card> result = cardRepository.search("Fire", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t3 rarity가 정확히 일치하는 카드만 조회한다")
    void t3() {
        Page<Card> result = cardRepository.search(null, "Common", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Pikachu");
    }

    @Test
    @DisplayName("t4 expansionId가 정확히 일치하는 카드만 조회한다")
    void t4() {
        Page<Card> result = cardRepository.search(null, null, "sv3pt5", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t5 여러 조건을 조합하면 AND로 필터링된다")
    void t5() {
        Page<Card> result = cardRepository.search("Fire", null, "base1", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t6 조건이 없으면 전체 카드를 페이지 크기만큼 반환한다")
    void t6() {
        Page<Card> result = cardRepository.search(null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("t7 같은 포켓몬 도감번호를 가진 다른 카드를 유사 카드로 조회한다")
    void t7() {
        List<Card> result = cardRepository.findRelatedByPokedexNumber(charizard.getId());

        assertThat(result)
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t8 유사 카드 조회 결과에서 자기 자신은 제외된다")
    void t8() {
        List<Card> result = cardRepository.findRelatedByPokedexNumber(charizard.getId());

        assertThat(result).extracting(Card::getId).doesNotContain(charizard.getId());
    }

    @Test
    @DisplayName("t9 도감번호가 없는 카드는 유사 카드 조회 결과가 빈 목록이다")
    void t9() {
        List<Card> result = cardRepository.findRelatedByPokedexNumber(professorsResearch.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t10 같은 세트에 속한 다른 카드를 유사 카드로 조회한다")
    void t10() {
        List<Card> result = cardRepository.findRelatedByExpansion("base1", professorsResearch.getId());

        assertThat(result)
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Blastoise", "Pikachu");
    }

    @Test
    @DisplayName("t11 같은 세트에 다른 카드가 없으면 유사 카드 조회 결과가 빈 목록이다")
    void t11() {
        List<Card> result = cardRepository.findRelatedByExpansion("swsh1", quickBall.getId());

        assertThat(result).isEmpty();
    }
}
