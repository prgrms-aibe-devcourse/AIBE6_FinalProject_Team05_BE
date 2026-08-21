package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.init.PokedexKoNameInitializer;
import com.pokade.domain.card.repository.PokedexKoNameJdbcRepository;
import com.pokade.domain.card.repository.PokedexKoNameRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.card.support.PokedexKoNameCache;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * 도감 한글명 CSV → DB 적재(PokedexKoNameInitializer) → 메모리 캐시(PokedexKoNameCache) →
 * CardService 응답(nameKo)까지 이어지는 전체 경로를 실제 Postgres(testcontainers)로 검증한다.
 * 단위 테스트(CardNameKoResolverTest, PokedexKoNameCacheTest)가 각각 mock으로 끊어서 봤던
 * 연결 지점을 실제 빈 배선 + 실제 DB로 이어서 확인하는 목적이다.
 */
@DataJpaTest
@Import({PokedexKoNameJdbcRepository.class, PokedexKoNameInitializer.class, PokedexKoNameCache.class,
        CardNameKoResolver.class, CardService.class, CardQueryService.class, CardFacetService.class})
class CardNameKoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PokedexKoNameInitializer pokedexKoNameInitializer;
    @Autowired
    private PokedexKoNameRepository pokedexKoNameRepository;
    @Autowired
    private PokedexKoNameCache pokedexKoNameCache;
    @Autowired
    private CardService cardService;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() throws Exception {
        pokedexKoNameInitializer.run(null);
    }

    @Test
    @DisplayName("t1 CSV 1025건이 DB에 적재되고, 재실행해도 스킵되어 중복 적재되지 않는다")
    void t1() throws Exception {
        assertThat(pokedexKoNameRepository.count()).isEqualTo(1025);

        pokedexKoNameInitializer.run(null);

        assertThat(pokedexKoNameRepository.count()).isEqualTo(1025);
    }

    @Test
    @DisplayName("t2 적재된 데이터가 메모리 캐시에도 실제로 반영된다")
    void t2() {
        assertThat(pokedexKoNameCache.getNameKo(25)).isEqualTo("피카츄");
        assertThat(pokedexKoNameCache.getNameKo(6)).isEqualTo("리자몽");
    }

    @Test
    @DisplayName("t3 도감번호가 있는 실제 카드는 CardService.getDetail() 응답의 nameKo가 정확히 치환되어 나온다")
    void t3() {
        Card charizardEx = persistCard("Charizard ex", "Pokémon", List.of(6));

        CardDetailResponse detail = cardService.getDetail(charizardEx.getId());

        assertThat(detail.nameKo()).isEqualTo("리자몽 ex");
    }

    @Test
    @DisplayName("t4 도감번호가 없는 트레이너 카드는 CardService.getDetail() 응답의 nameKo가 null이다")
    void t4() {
        Card quickBall = persistCard("Quick Ball", "Trainer", null);

        CardDetailResponse detail = cardService.getDetail(quickBall.getId());

        assertThat(detail.nameKo()).isNull();
    }

    private Card persistCard(String name, String supertype, List<Integer> pokedexNumbers) {
        Card card = Card.builder()
                .name(name)
                .supertype(supertype)
                .nationalPokedexNumbers(pokedexNumbers)
                .build();
        entityManager.persist(card);
        return card;
    }
}
