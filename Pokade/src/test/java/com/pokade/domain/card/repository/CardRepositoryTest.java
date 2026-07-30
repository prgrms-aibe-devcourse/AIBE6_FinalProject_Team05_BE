package com.pokade.domain.card.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t3 rarity가 정확히 일치하는 카드만 조회한다")
    void t3() {
        Page<Card> result = cardRepository.search(null, List.of("Common"), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Pikachu");
    }

    @Test
    @DisplayName("t4 expansionId가 정확히 일치하는 카드만 조회한다")
    void t4() {
        Page<Card> result = cardRepository.search(null, null, "sv3pt5", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t5 여러 조건을 조합하면 AND로 필터링된다")
    void t5() {
        Page<Card> result = cardRepository.search(List.of("Fire"), null, "base1", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t6 조건이 없으면 전체 카드를 페이지 크기만큼 반환한다")
    void t6() {
        Page<Card> result = cardRepository.search(null, null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("t12 타입을 여러 개 선택하면 하나라도 포함된 카드를 OR로 조회한다")
    void t12() {
        Page<Card> result = cardRepository.search(List.of("Fire", "Water"), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Blastoise", "Charizard ex");
    }

    @Test
    @DisplayName("t13 레어도를 여러 개 선택하면 하나라도 일치하는 카드를 OR로 조회한다")
    void t13() {
        Page<Card> result = cardRepository.search(null, List.of("Common", "Double Rare"), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Pikachu", "Charizard ex");
    }

    @Test
    @DisplayName("t14 타입·레어도·세트를 동시에 지정하면 AND로 결합되어 모두 만족하는 카드만 조회한다")
    void t14() {
        Page<Card> result = cardRepository.search(
                List.of("Fire"), List.of("Rare Holo"), "base1", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t15 조건을 모두 만족하는 카드가 없으면 빈 페이지를 반환한다")
    void t15() {
        Page<Card> result = cardRepository.search(List.of("Water"), List.of("Common"), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("t16 존재하지 않는 타입 값으로 조회해도 예외 없이 빈 페이지를 반환한다")
    void t16() {
        Page<Card> result = cardRepository.search(List.of("NonExistentType"), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("t17 타입을 여러 개, 레어도를 여러 개 동시에 선택하면 각 조건 내부는 OR, 조건 간에는 AND로 결합된다")
    void t17() {
        Page<Card> result = cardRepository.search(
                List.of("Fire", "Water"), List.of("Rare Holo", "Double Rare"), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Blastoise", "Charizard ex");
    }

    @Test
    @DisplayName("t18 sort가 없으면 기본값 latest 기준(synced_at DESC, id DESC)으로 정렬한다")
    void t18() {
        Page<Card> result = cardRepository.search(null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Quick Ball", "Professor's Research", "Charizard ex", "Pikachu", "Blastoise", "Charizard");
    }

    @Test
    @DisplayName("t19 sort=name이면 이름 오름차순으로 정렬한다")
    void t19() {
        Page<Card> result = cardRepository.search(null, null, null, "name", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Blastoise", "Charizard", "Charizard ex", "Pikachu", "Professor's Research", "Quick Ball");
    }

    @Test
    @DisplayName("t20 화이트리스트에 없는 sort 값이 들어와도 예외 없이 기본값 latest로 처리한다")
    void t20() {
        Page<Card> result = cardRepository.search(null, null, null, "id; DROP TABLE cards;--", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Quick Ball", "Professor's Research", "Charizard ex", "Pikachu", "Blastoise", "Charizard");
    }

    @Test
    @DisplayName("t21 필터와 sort=name을 함께 적용해도 필터링된 결과 안에서 이름순으로 정렬한다")
    void t21() {
        Page<Card> result = cardRepository.search(List.of("Fire", "Water"), null, null, "name", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Blastoise", "Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t22 sort=popular이면 조회수 내림차순으로 정렬한다")
    void t22() {
        Expansion popExpansion = persistExpansion("popTest", "Popularity Test");
        persistCardWithViewCount("Mewtwo", popExpansion, 50);
        persistCardWithViewCount("Squirtle", popExpansion, 10);
        persistCardWithViewCount("Bulbasaur", popExpansion, 0);

        Page<Card> result = cardRepository.search(null, null, "popTest", "popular", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Mewtwo", "Squirtle", "Bulbasaur");
    }

    @Test
    @DisplayName("t23 필터와 sort=popular를 함께 적용해도 필터링된 결과 안에서 조회수순으로 정렬한다")
    void t23() {
        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, "popular", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t24 상세 조회 시 view_count를 원자적 UPDATE로 1 증가시킨다")
    void t24() {
        cardRepository.incrementViewCount(charizard.getId());
        entityManager.flush();
        entityManager.clear();

        Card reloaded = cardRepository.findById(charizard.getId()).orElseThrow();
        assertThat(reloaded.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("t25 여러 커넥션에서 동시에 조회수를 증가시켜도 유실 없이 정확히 누적된다")
    void t25() throws InterruptedException {
        // setUp()이 만든 데이터는 이 테스트 메서드가 끝나면 롤백되는 트랜잭션에 속해 있어 다른 커넥션에서 보이지 않는다.
        // 동시성 검증을 위해 REQUIRES_NEW로 별도 트랜잭션을 커밋해 전용 카드를 만들고, 검증 후 직접 정리한다.
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long cardId = requiresNew.execute(status -> {
            Card card = Card.builder().name("Concurrency Test Card").build();
            entityManager.persist(card);
            entityManager.flush();
            return card.getId();
        });

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> cardRepository.incrementViewCount(cardId)));
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        // future.get()으로 각 스레드의 예외를 즉시 드러낸다. submit()만 하고 get()을 부르지 않으면
        // TransactionRequiredException 등이 조용히 삼켜져 view_count가 증가하지 않은 원인을 놓치게 된다.
        for (Future<?> future : futures) {
            assertThatCode(future::get).doesNotThrowAnyException();
        }

        try {
            Integer finalViewCount = requiresNew.execute(status ->
                    cardRepository.findById(cardId).orElseThrow().getViewCount());
            assertThat(finalViewCount).isEqualTo(threadCount);
        } finally {
            requiresNew.executeWithoutResult(status -> cardRepository.deleteById(cardId));
        }
    }

    private Card persistCardWithViewCount(String name, Expansion expansion, int viewCount) {
        Card card = Card.builder()
                .name(name)
                .expansion(expansion)
                .viewCount(viewCount)
                .build();
        entityManager.persist(card);
        return card;
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
