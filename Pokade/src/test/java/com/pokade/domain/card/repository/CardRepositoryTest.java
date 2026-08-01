package com.pokade.domain.card.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
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

        // synced_at을 삽입 순서(id)와 일부러 어긋나게 부여해, DESC 정렬이 id가 아닌
        // synced_at 자체를 1차 키로 쓰는지 구분해서 검증할 수 있게 한다.
        LocalDateTime baseTime = LocalDateTime.now();
        charizard = persistCard("Charizard", "Rare Holo", base1, "Fire", List.of(6), baseTime.plusMinutes(5));
        persistCard("Blastoise", "Rare Holo", base1, "Water", List.of(9), baseTime.plusMinutes(1));
        persistCard("Pikachu", "Common", base1, "Lightning", List.of(25), baseTime.plusMinutes(4));
        charizardEx = persistCard("Charizard ex", "Double Rare", sv3pt5, "Fire", List.of(6), baseTime.plusMinutes(6));
        professorsResearch = persistTrainerCard("Professor's Research", base1, baseTime.plusMinutes(2));
        quickBall = persistTrainerCard("Quick Ball", swsh1, baseTime.plusMinutes(3));
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

    private Card persistCard(String name, String rarity, Expansion expansion, String type, List<Integer> pokedexNumbers, LocalDateTime syncedAt) {
        Card card = Card.builder()
                .name(name)
                .rarity(rarity)
                .supertype("Pokémon")
                .expansion(expansion)
                .types(type != null ? List.of(type) : null)
                .nationalPokedexNumbers(pokedexNumbers)
                .syncedAt(syncedAt)
                .build();
        entityManager.persist(card);
        return card;
    }

    private Card persistTrainerCard(String name, Expansion expansion, LocalDateTime syncedAt) {
        Card card = Card.builder()
                .name(name)
                .supertype("Trainer")
                .expansion(expansion)
                .syncedAt(syncedAt)
                .build();
        entityManager.persist(card);
        return card;
    }

    private Long persistSeller(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) "
                                + "VALUES (:email, 'tester', 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .getSingleResult()).longValue();
    }

    private void persistListing(Long cardId, Long sellerId, ListingGrade grade, ListingStatus status) {
        persistListing(cardId, sellerId, 10000, grade, status);
    }

    private void persistListing(Long cardId, Long sellerId, int price, ListingGrade grade, ListingStatus status) {
        Listing listing = Listing.builder()
                .cardId(cardId)
                .sellerId(sellerId)
                .price(price)
                .grade(grade)
                .build();
        entityManager.persist(listing);
        if (status != ListingStatus.ACTIVE) {
            entityManager.createNativeQuery("UPDATE listings SET status = :status WHERE id = :id")
                    .setParameter("status", status.name())
                    .setParameter("id", listing.getId())
                    .executeUpdate();
        }
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
        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t3 rarity가 정확히 일치하는 카드만 조회한다")
    void t3() {
        Page<Card> result = cardRepository.search(null, List.of("Common"), null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Pikachu");
    }

    @Test
    @DisplayName("t4 expansionId가 정확히 일치하는 카드만 조회한다")
    void t4() {
        Page<Card> result = cardRepository.search(null, null, null, "sv3pt5", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t5 여러 조건을 조합하면 AND로 필터링된다")
    void t5() {
        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, "base1", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t6 조건이 없으면 전체 카드를 페이지 크기만큼 반환한다")
    void t6() {
        Page<Card> result = cardRepository.search(null, null, null, null, null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("t12 타입을 여러 개 선택하면 하나라도 포함된 카드를 OR로 조회한다")
    void t12() {
        Page<Card> result = cardRepository.search(List.of("Fire", "Water"), null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Blastoise", "Charizard ex");
    }

    @Test
    @DisplayName("t13 레어도를 여러 개 선택하면 하나라도 일치하는 카드를 OR로 조회한다")
    void t13() {
        Page<Card> result = cardRepository.search(null, List.of("Common", "Double Rare"), null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Pikachu", "Charizard ex");
    }

    @Test
    @DisplayName("t14 타입·레어도·세트를 동시에 지정하면 AND로 결합되어 모두 만족하는 카드만 조회한다")
    void t14() {
        Page<Card> result = cardRepository.search(
                List.of("Fire"), List.of("Rare Holo"), null, "base1", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t15 조건을 모두 만족하는 카드가 없으면 빈 페이지를 반환한다")
    void t15() {
        Page<Card> result = cardRepository.search(List.of("Water"), List.of("Common"), null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("t16 존재하지 않는 타입 값으로 조회해도 예외 없이 빈 페이지를 반환한다")
    void t16() {
        Page<Card> result = cardRepository.search(List.of("NonExistentType"), null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("t17 타입을 여러 개, 레어도를 여러 개 동시에 선택하면 각 조건 내부는 OR, 조건 간에는 AND로 결합된다")
    void t17() {
        Page<Card> result = cardRepository.search(
                List.of("Fire", "Water"), List.of("Rare Holo", "Double Rare"), null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Blastoise", "Charizard ex");
    }

    @Test
    @DisplayName("t18 sort가 없으면 기본값 latest 기준(synced_at DESC, id DESC)으로 정렬한다")
    void t18() {
        Page<Card> result = cardRepository.search(null, null, null, null, null, null, null, PageRequest.of(0, 10));

        // id 순서(삽입 순서)와는 다른 synced_at 순서(내림차순: Charizard ex > Charizard > Pikachu
        // > Quick Ball > Professor's Research > Blastoise)로 나와야 synced_at이 실제 1차 정렬
        // 키임이 증명된다.
        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex", "Charizard", "Pikachu", "Quick Ball", "Professor's Research", "Blastoise");
    }

    @Test
    @DisplayName("t19 sort=name이면 이름 오름차순으로 정렬한다")
    void t19() {
        Page<Card> result = cardRepository.search(null, null, null, null, null, null, "name", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Blastoise", "Charizard", "Charizard ex", "Pikachu", "Professor's Research", "Quick Ball");
    }

    @Test
    @DisplayName("t20 화이트리스트에 없는 sort 값이 들어와도 예외 없이 기본값 latest로 처리한다")
    void t20() {
        Page<Card> result = cardRepository.search(null, null, null, null, null, null, "id; DROP TABLE cards;--", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex", "Charizard", "Pikachu", "Quick Ball", "Professor's Research", "Blastoise");
    }

    @Test
    @DisplayName("t21 필터와 sort=name을 함께 적용해도 필터링된 결과 안에서 이름순으로 정렬한다")
    void t21() {
        Page<Card> result = cardRepository.search(List.of("Fire", "Water"), null, null, null, null, null, "name", PageRequest.of(0, 10));

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

        Page<Card> result = cardRepository.search(null, null, null, "popTest", null, null, "popular", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Mewtwo", "Squirtle", "Bulbasaur");
    }

    @Test
    @DisplayName("t23 필터와 sort=popular를 함께 적용해도 필터링된 결과 안에서 조회수순으로 정렬한다")
    void t23() {
        cardRepository.incrementViewCount(charizard.getId());
        cardRepository.incrementViewCount(charizard.getId());
        cardRepository.incrementViewCount(charizardEx.getId());
        entityManager.flush();
        entityManager.clear();

        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, null, null, null, "popular", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard", "Charizard ex");
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

        // 카드 커밋 직후부터 finally로 정리를 보장한다: 스레드 실행/검증 중 어디서 실패하더라도
        // 커밋된 테스트 카드가 DB에 남지 않도록 try 범위를 넓혔다.
        try {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> cardRepository.incrementViewCount(cardId)));
            }
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            // awaitTermination의 반환값을 무시하면 타임아웃으로 일부 스레드가 아직 실행 중이어도
            // 조용히 다음 단계로 넘어가 원인 파악이 어려운 실패로 이어질 수 있다.
            assertThat(terminated).as("20개 스레드가 타임아웃 전에 모두 종료되어야 한다").isTrue();
            // future.get()으로 각 스레드의 예외를 즉시 드러낸다. submit()만 하고 get()을 부르지 않으면
            // TransactionRequiredException 등이 조용히 삼켜져 view_count가 증가하지 않은 원인을 놓치게 된다.
            for (Future<?> future : futures) {
                assertThatCode(future::get).doesNotThrowAnyException();
            }

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
    @DisplayName("t28 types에 빈 문자열이 섞여 있으면 제거하고 나머지 값으로만 필터링한다")
    void t28() {
        Page<Card> result = cardRepository.search(List.of("Fire", ""), null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t29 rarity에 빈 문자열이 섞여 있으면 제거하고 나머지 값으로만 필터링한다")
    void t29() {
        Page<Card> result = cardRepository.search(null, List.of("Common", ""), null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Pikachu");
    }

    @Test
    @DisplayName("t30 types가 빈 문자열로만 채워져 있으면 필터 없이 전체 카드를 반환한다")
    void t30() {
        Page<Card> result = cardRepository.search(List.of(""), null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(6);
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

    @Test
    @DisplayName("t26 external_id로 카드를 조회한다")
    void t26() {
        Card card = Card.builder()
                .name("Mew ex")
                .externalId("sv3pt5-151")
                .build();
        entityManager.persist(card);

        Optional<Card> result = cardRepository.findByExternalId("sv3pt5-151");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Mew ex");
    }

    @Test
    @DisplayName("t27 존재하지 않는 external_id로 조회하면 빈 Optional을 반환한다")
    void t27() {
        Optional<Card> result = cardRepository.findByExternalId("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t35 grades=S로 필터링하면 S등급 매물이 있는 카드만 조회된다")
    void t35() {
        Long seller = persistSeller("grade-s-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, ListingGrade.A, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, List.of("S"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t36 grades를 여러 개 선택하면 하나라도 일치하는 매물을 가진 카드를 OR로 조회한다")
    void t36() {
        Long seller = persistSeller("grade-multi-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, ListingGrade.A, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, List.of("S", "A"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactlyInAnyOrder("Charizard", "Charizard ex");
    }

    @Test
    @DisplayName("t37 ACTIVE가 아닌 매물의 등급은 필터에 반영되지 않는다")
    void t37() {
        Long seller = persistSeller("grade-cancelled-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.CANCELLED);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, List.of("S"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("t38 등급 필터와 일치하는 매물이 없는 카드는 결과에서 제외된다")
    void t38() {
        Long seller = persistSeller("grade-none-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.PSA10, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, List.of("S"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("t39 rarity와 grades를 함께 적용하면 같은 카드가 두 조건을 모두 만족할 때만 조회된다")
    void t39() {
        Long seller = persistSeller("grade-rarity-seller@test.com");
        // charizard와 blastoise는 둘 다 rarity=Rare Holo. charizard에만 S등급 매물을 붙여
        // rarity 조건과 grades 조건이 "같은 카드" 기준으로 AND 결합되는지 검증한다.
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, List.of("Rare Holo"), List.of("S"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard");
    }

    @Test
    @DisplayName("t40 grades가 빈 문자열로만 채워져 있으면 필터 없이 전체 카드를 반환한다")
    void t40() {
        Page<Card> result = cardRepository.search(null, null, List.of(""), null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("t43 등급 미지정 상태에서 가격(minPrice)만 지정해도 매물 가격 기준으로 필터링된다")
    void t43() {
        // 등급을 지정하지 않고 가격만 지정한 경우에도 바깥 게이트 조건이 EXISTS를 실행해야
        // 매물 가격 기준 필터링이 실제로 적용되는지 검증한다(게이트 수정의 핵심 케이스).
        Long seller = persistSeller("price-only-seller@test.com");
        persistListing(charizard.getId(), seller, 5000, null, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, 20000, null, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, null, null, 10000, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t44 minPrice와 maxPrice를 함께 지정하면 범위 안의 매물이 있는 카드만 조회된다")
    void t44() {
        Long seller = persistSeller("price-range-seller@test.com");
        persistListing(charizard.getId(), seller, 5000, null, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, 15000, null, ListingStatus.ACTIVE);
        persistListing(professorsResearch.getId(), seller, 30000, null, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, null, null, 10000, 20000, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t45 등급과 가격을 동시에 지정하면 같은 매물이 두 조건을 모두 만족하는 카드만 조회된다")
    void t45() {
        Long seller = persistSeller("grade-price-seller@test.com");
        // charizard는 두 매물로 조건을 나눠 만족시켜, 같은 매물 하나가 등급·가격을 모두
        // 만족해야 하는지(카드 단위가 아니라 매물 단위 AND) 검증한다.
        persistListing(charizard.getId(), seller, 5000, ListingGrade.S, ListingStatus.ACTIVE);
        persistListing(charizard.getId(), seller, 20000, ListingGrade.A, ListingStatus.ACTIVE);
        // charizardEx는 단일 매물이 등급·가격 조건을 모두 만족한다.
        persistListing(charizardEx.getId(), seller, 20000, ListingGrade.S, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(null, null, List.of("S"), null, 10000, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t46 가격 필터를 기존 타입 필터와 함께 적용해도 두 조건이 AND로 결합된다")
    void t46() {
        Long seller = persistSeller("price-type-seller@test.com");
        persistListing(charizard.getId(), seller, 5000, null, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, 20000, null, ListingStatus.ACTIVE);
        entityManager.flush();

        Page<Card> result = cardRepository.search(List.of("Fire"), null, null, null, 10000, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Card::getName)
                .containsExactly("Charizard ex");
    }

    @Test
    @DisplayName("t41 카드별 ACTIVE 매물에 존재하는 등급을 배치로 조회하면 카드마다 중복 없는 등급 목록을 얻는다")
    void t41() {
        Long seller = persistSeller("grade-batch-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.ACTIVE);
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.ACTIVE);
        persistListing(charizard.getId(), seller, ListingGrade.A, ListingStatus.ACTIVE);
        persistListing(charizardEx.getId(), seller, ListingGrade.B, ListingStatus.ACTIVE);
        entityManager.flush();

        List<CardRepository.CardGradeView> result = cardRepository.findGradesByCardIds(
                List.of(charizard.getId(), charizardEx.getId(), professorsResearch.getId()), List.of("S", "A", "B"));

        assertThat(result)
                .filteredOn(view -> view.getCardId().equals(charizard.getId()))
                .extracting(CardRepository.CardGradeView::getGrade)
                .containsExactlyInAnyOrder("S", "A");
        assertThat(result)
                .filteredOn(view -> view.getCardId().equals(charizardEx.getId()))
                .extracting(CardRepository.CardGradeView::getGrade)
                .containsExactly("B");
        assertThat(result)
                .noneMatch(view -> view.getCardId().equals(professorsResearch.getId()));
    }

    @Test
    @DisplayName("t42 ACTIVE가 아니거나 S/A/B가 아닌 등급은 배치 조회에서 제외된다")
    void t42() {
        Long seller = persistSeller("grade-batch-exclude-seller@test.com");
        persistListing(charizard.getId(), seller, ListingGrade.PSA10, ListingStatus.ACTIVE);
        persistListing(charizard.getId(), seller, ListingGrade.S, ListingStatus.CANCELLED);
        entityManager.flush();

        List<CardRepository.CardGradeView> result = cardRepository.findGradesByCardIds(List.of(charizard.getId()), List.of("S", "A", "B"));

        assertThat(result).isEmpty();
    }
}
