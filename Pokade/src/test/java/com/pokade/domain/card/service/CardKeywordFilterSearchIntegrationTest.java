package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.card.support.PokedexKoNameCache;
import com.pokade.support.AbstractIntegrationTest;
import com.pokade.support.TestMetricsConfig;

import jakarta.persistence.EntityManager;

/**
 * #308 설계 승인안(폴백 판단 3단계)을 실제 Postgres(testcontainers, pg_trgm 포함)로 검증한다.
 * CardQueryServiceTest(Mockito 단위 테스트)는 리포지토리를 mock으로 대체해 SQL의 실제 동작(특히
 * similarity() 임계값, ILIKE 매칭)을 검증하지 못하므로, 이 통합 테스트가 그 공백을 메운다.
 * CardNameKoIntegrationTest와 동일한 패턴(@DataJpaTest + 필요한 빈만 @Import)을 따른다.
 *
 * 시나리오 3종 × 한글/영문 = 6개 테스트:
 * ① 필터+정확일치 있음 → 그대로 반환.
 * ② 필터+정확일치 0건, 필터 없이는 정확일치 있음 → "필터가 세서 없는 것" - 폴백 안 탐, 빈 페이지.
 * ③ 필터+정확일치 0건, 필터 없이도 0건(키워드 자체가 애매) → 유사도 폴백 탐, 폴백 결과에도 필터 적용됨.
 */
@DataJpaTest
@Import({CardNameKoResolver.class, PokedexKoNameCache.class, CardQueryService.class, TestMetricsConfig.class})
class CardKeywordFilterSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CardQueryService cardQueryService;

    @Autowired
    private EntityManager entityManager;

    private final Pageable pageable = PageRequest.of(0, 20);

    private Card persistCard(String name, String rarity, List<String> types, List<Integer> pokedexNumbers) {
        Card card = Card.builder()
                .name(name)
                .rarity(rarity)
                .types(types)
                .nationalPokedexNumbers(pokedexNumbers)
                .build();
        entityManager.persist(card);
        return card;
    }

    private PokedexKoName persistPokedexKoName(int pokedexNumber, String nameKo, String nameKoChosung) {
        PokedexKoName pokedexKoName = PokedexKoName.builder()
                .pokedexNumber(pokedexNumber)
                .nameEn("dummy")
                .nameKo(nameKo)
                .nameKoChosung(nameKoChosung)
                .build();
        entityManager.persist(pokedexKoName);
        return pokedexKoName;
    }

    private Page<CardResponse> searchWithTypeFilter(String q, String type) {
        return cardQueryService.search(q, type == null ? null : List.of(type), null, null, null, null, null, null, pageable);
    }

    // ===== 영문 경로(searchByName) =====

    @Test
    @DisplayName("영문 ① 필터+정확일치가 모두 있으면 그대로 반환하고 fuzzyMatch=false다")
    void english_scenario1_filteredExactMatch_returnsAsIs() {
        persistCard("Charizard", "Rare Holo", List.of("Fire"), null);
        entityManager.flush();

        Page<CardResponse> result = searchWithTypeFilter("Charizard", "Fire");

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(false);
    }

    @Test
    @DisplayName("영문 ② 필터+정확일치는 0건이지만 필터 없는 정확일치는 존재하면 빈 페이지를 반환하고 유사도 폴백을 태우지 않는다")
    void english_scenario2_filteredExactMatchEmpty_butUnfilteredExists_doesNotFallBack() {
        persistCard("Charizard", "Rare Holo", List.of("Fire"), null);
        // "Charizand"는 "Charizard"와 트라이그램 유사도가 높아(실측 0.53), 폴백이 잘못 발동하면
        // Water 필터에 걸려 결과에 섞여 들어올 수 있는 회귀 감지용 디코이 카드다.
        persistCard("Charizand", "Common", List.of("Water"), null);
        entityManager.flush();

        Page<CardResponse> result = searchWithTypeFilter("Charizard", "Water");

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("영문 ③ 필터 없이도 정확일치가 0건(키워드 자체가 애매)이면 유사도 폴백을 태우고, 폴백 결과에도 필터가 적용된다")
    void english_scenario3_noExactMatchEvenWithoutFilter_fallsBackWithFilterApplied() {
        persistCard("Charizard", "Rare Holo", List.of("Fire"), null);
        persistCard("Charmeleon", "Common", List.of("Water"), null);
        entityManager.flush();

        // "Charizrd"는 두 카드 이름 어디에도 부분일치하지 않지만(사전 확인), pg_trgm 유사도는
        // Charizard/Charmeleon 둘 다 0.14 임계값을 넘는다(실측 각각 0.33).
        Page<CardResponse> result = searchWithTypeFilter("Charizrd", "Fire");

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(true);
    }

    // ===== 한글 경로(searchByPokedexKoName) =====

    @Test
    @DisplayName("한글 ① 필터+정확일치가 모두 있으면 그대로 반환하고 fuzzyMatch=false다")
    void korean_scenario1_filteredExactMatch_returnsAsIs() {
        persistPokedexKoName(6, "리자몽", "ㄹㅈㅁ");
        persistCard("Charizard", "Rare Holo", List.of("Fire"), List.of(6));
        entityManager.flush();

        Page<CardResponse> result = searchWithTypeFilter("리자몽", "Fire");

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(false);
    }

    @Test
    @DisplayName("한글 ② 필터+정확일치는 0건이지만 필터 없는 정확일치는 존재하면 빈 페이지를 반환하고 유사도 폴백을 태우지 않는다")
    void korean_scenario2_filteredExactMatchEmpty_butUnfilteredExists_doesNotFallBack() {
        persistPokedexKoName(6, "리자몽", "ㄹㅈㅁ");
        persistCard("Charizard", "Rare Holo", List.of("Fire"), List.of(6));
        entityManager.flush();

        Page<CardResponse> result = searchWithTypeFilter("리자몽", "Water");

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("한글 ③ 필터 없이도 정확일치가 0건(키워드 자체가 애매)이면 유사도 폴백을 태우고, 폴백 결과에도 필터가 적용된다")
    void korean_scenario3_noExactMatchEvenWithoutFilter_fallsBackWithFilterApplied() {
        persistPokedexKoName(6, "리자몽", "ㄹㅈㅁ");
        persistPokedexKoName(5, "리자드", "ㄹㅈㄷ");
        persistCard("Charizard", "Rare Holo", List.of("Fire"), List.of(6));
        persistCard("Charmeleon", "Common", List.of("Water"), List.of(5));
        entityManager.flush();

        // "리자옹"은 "리자몽"/"리자드" 어디에도 부분일치하지 않지만(사전 확인), pg_trgm 유사도는
        // 둘 다 0.14 임계값을 넘는다(실측 각각 0.33).
        Page<CardResponse> result = searchWithTypeFilter("리자옹", "Fire");

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(true);
    }
}
