package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.PokedexKoNameRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CardQueryServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private PokedexKoNameRepository pokedexKoNameRepository;

    @Mock
    private CardNameKoResolver cardNameKoResolver;

    @InjectMocks
    private CardQueryService cardQueryService;

    // #308: searchByKeyword()는 필터 없이(모두 false/빈 값) 9-인자 search()로 위임하므로,
    // 키워드 전용 검색 테스트들이 새 필터+키워드 결합 리포지토리 메서드를 "필터 없음" 값으로 스텁할 때 재사용한다.
    private static final String[] NO_TYPES = new String[0];
    private static final List<String> NO_RARITIES = List.of("");
    private static final List<String> NO_LANGUAGES = List.of("");

    @Test
    @DisplayName("t1 검색 조건을 리포지토리에 위임하고 결과를 응답 DTO 목록으로 변환한다")
    void t1() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(List.of("Fire", "炎"), List.of("Rare Holo"), "base1", null, null, "name", pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(List.of("Fire"), List.of("Rare Holo"), "base1", null, null, "name", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t13 타입·레어도를 여러 개 선택해도 리포지토리에 그대로 위임한다")
    void t13() {
        Card card = Card.builder()
                .id(1L)
                .name("Blastoise")
                .types(List.of("Water"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(List.of("Fire", "炎", "Water", "水"), List.of("Common", "通常", "Rare Holo"), null, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.search(
                List.of("Fire", "Water"), List.of("Common", "Rare Holo"), null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Blastoise");
    }

    @Test
    @DisplayName("t14 sort 파라미터를 리포지토리에 그대로 위임한다")
    void t14() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(null, null, null, null, null, "latest", pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, "latest", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t17 size가 100 이하이면 정상 처리된다")
    void t17() {
        Pageable pageable = PageRequest.of(0, 100);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t18 size가 100을 초과하면 INVALID_INPUT 예외가 발생한다")
    void t18() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> cardQueryService.search(null, null, null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t19 types가 20개를 초과하면 INVALID_INPUT 예외가 발생한다")
    void t19() {
        Pageable pageable = PageRequest.of(0, 20);
        List<String> tooManyTypes = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "type" + i)
                .toList();

        assertThatThrownBy(() -> cardQueryService.search(tooManyTypes, null, null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t20 rarity가 20개를 초과하면 INVALID_INPUT 예외가 발생한다")
    void t20() {
        Pageable pageable = PageRequest.of(0, 20);
        List<String> tooManyRarities = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "rarity" + i)
                .toList();

        assertThatThrownBy(() -> cardQueryService.search(null, tooManyRarities, null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t21 types가 정확히 20개이면 정상 처리된다(경계값)")
    void t21() {
        Pageable pageable = PageRequest.of(0, 20);
        List<String> exactlyTwenty = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> "type" + i)
                .toList();
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(exactlyTwenty, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(exactlyTwenty, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t41 minPrice가 maxPrice보다 크면 INVALID_INPUT 예외가 발생한다")
    void t41() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardQueryService.search(null, null, null, 20000, 10000, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t42 minPrice/maxPrice를 리포지토리에 그대로 위임한다")
    void t42() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(null, null, null, 10000, 50000, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, 10000, 50000, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t46 레어도 코드가 매핑되어 있으면 표준 명칭으로 변환되고, 타입 매핑도 함께 정상 동작한다")
    void t46() {
        Card card = Card.builder()
                .id(1L)
                .name("クヌギダマ")
                .rarity("通常")
                .rarityCode("●")
                .types(List.of("草"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getContent().get(0).rarity()).isEqualTo("Common");
        assertThat(result.getContent().get(0).types()).containsExactly("Grass");
    }

    @Test
    @DisplayName("t47 매핑에 없는 레어도 코드는 원본 rarity 값을 그대로 유지한다")
    void t47() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .rarity("Hyper Rare")
                .rarityCode("★★★")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getContent().get(0).rarity()).isEqualTo("Hyper Rare");
    }

    @Test
    @DisplayName("t35 검색 결과 카드들의 id를 모아 등급을 배치 조회하고 카드별 등급 배열로 매핑한다")
    void t35() {
        Card charizard = Card.builder().id(1L).name("Charizard").types(List.of("Fire")).build();
        Card blastoise = Card.builder().id(2L).name("Blastoise").types(List.of("Water")).build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(charizard, blastoise), pageable, 2);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);
        given(cardRepository.findGradesByCardIds(eq(List.of(1L, 2L)), any())).willReturn(List.of(
                gradeView(1L, "B"),
                gradeView(1L, "S"),
                gradeView(1L, "A")
        ));

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getContent().get(0).grades()).containsExactly("S", "A", "B");
        assertThat(result.getContent().get(1).grades()).isEmpty();
        verify(cardRepository, times(1)).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t36 검색 결과가 비어 있으면 등급 배치 조회를 호출하지 않는다")
    void t36() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardQueryService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(cardRepository, never()).findGradesByCardIds(any(), any());
    }

    private CardRepository.CardGradeView gradeView(Long cardId, String grade) {
        return new CardRepository.CardGradeView() {
            @Override
            public Long getCardId() {
                return cardId;
            }

            @Override
            public String getGrade() {
                return grade;
            }
        };
    }

    private CardVariantRepository.VariantGradeView variantGradeView(Long variantId, String grade) {
        return new CardVariantRepository.VariantGradeView() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public String getGrade() {
                return grade;
            }
        };
    }

    @Test
    @DisplayName("t2 존재하는 id로 상세조회하면 확장팩과 변형 목록을 포함한 상세 응답을 반환한다")
    void t2() {
        Expansion expansion = Expansion.builder()
                .id("base1")
                .name("Base")
                .syncedAt(LocalDateTime.now())
                .build();
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .rarity("Rare Holo")
                .types(List.of("Fire"))
                .expansion(expansion)
                .build();
        CardVariant primaryVariant = CardVariant.builder()
                .id(1L)
                .card(card)
                .variantName("unlimitedHolofoil")
                .primary(true)
                .syncedAt(LocalDateTime.now())
                .build();
        CardVariant secondaryVariant = CardVariant.builder()
                .id(2L)
                .card(card)
                .variantName("firstEditionHolofoil")
                .primary(false)
                .syncedAt(LocalDateTime.now())
                .build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardVariantRepository.findByCardIdOrderByPrimaryDescVariantNameAsc(1L))
                .willReturn(List.of(primaryVariant, secondaryVariant));
        given(cardVariantRepository.findGradesByCardId(eq(1L), any())).willReturn(List.of(
                variantGradeView(1L, "A"),
                variantGradeView(2L, "B")
        ));

        CardDetailResponse result = cardQueryService.getDetail(1L);

        assertThat(result.name()).isEqualTo("Charizard");
        assertThat(result.expansion().id()).isEqualTo("base1");
        assertThat(result.variants()).hasSize(2);
        assertThat(result.variants().get(0).variantName()).isEqualTo("unlimitedHolofoil");
        assertThat(result.variants().get(0).grades()).containsExactly("A");
        assertThat(result.variants().get(1).grades()).containsExactly("B");
        verify(cardRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("t3 존재하지 않는 id로 상세조회하면 CARD_NOT_FOUND 예외가 발생한다")
    void t3() {
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardQueryService.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(cardRepository, never()).incrementViewCount(any());
    }

    @Test
    @DisplayName("t4 이름 키워드로 검색하면 리포지토리에 위임하고 결과를 응답 DTO 목록으로 변환한다")
    void t4() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.searchByNameOrderByName("char", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("char", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
        assertThat(result.getContent().get(0).fuzzyMatch()).isFalse();
    }

    @Test
    @DisplayName("t5 검색어가 없으면 INVALID_INPUT 예외가 발생한다")
    void t5() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardQueryService.searchByKeyword(null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t6 검색어가 공백뿐이면 INVALID_INPUT 예외가 발생한다")
    void t6() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardQueryService.searchByKeyword("   ", pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t22 검색어가 100자를 초과하면 INVALID_INPUT 예외가 발생한다")
    void t22() {
        Pageable pageable = PageRequest.of(0, 20);
        String tooLongKeyword = "a".repeat(101);

        assertThatThrownBy(() -> cardQueryService.searchByKeyword(tooLongKeyword, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t23 검색어가 정확히 100자이면 정상 처리된다(경계값)")
    void t23() {
        Pageable pageable = PageRequest.of(0, 20);
        String exactlyHundred = "a".repeat(100);
        Card card = Card.builder().id(1L).name("Charizard").types(List.of("Fire")).build();
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.searchByNameOrderByName(exactlyHundred, false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword(exactlyHundred, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("t24 키워드 검색에서 size가 100을 초과하면 INVALID_INPUT 예외가 발생한다")
    void t24() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> cardQueryService.searchByKeyword("char", pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t37 키워드 검색 결과 카드들의 id를 모아 등급을 배치 조회하고 카드별 등급 배열로 매핑한다")
    void t37() {
        Card charizard = Card.builder().id(1L).name("Charizard").types(List.of("Fire")).build();
        Card blastoise = Card.builder().id(2L).name("Blastoise").types(List.of("Water")).build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(charizard, blastoise), pageable, 2);
        given(cardRepository.searchByNameOrderByName("char", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);
        given(cardRepository.findGradesByCardIds(eq(List.of(1L, 2L)), any())).willReturn(List.of(
                gradeView(1L, "B"),
                gradeView(1L, "S")
        ));

        Page<CardResponse> result = cardQueryService.searchByKeyword("char", pageable);

        assertThat(result.getContent().get(0).grades()).containsExactly("S", "B");
        assertThat(result.getContent().get(1).grades()).isEmpty();
        verify(cardRepository, times(1)).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t38 키워드 검색 결과가 비어 있으면 등급 배치 조회를 호출하지 않는다")
    void t38() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.searchByNameOrderByName("char", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);
        given(cardRepository.existsByNameContainingIgnoreCase("char")).willReturn(false);
        // #187: 정확 검색이 0건이면 유사도 폴백을 시도한다 - 폴백도 0건이어야 최종 결과가 비게 된다.
        given(cardRepository.searchByNameSimilarToWithFilters("char", 0.14, false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("char", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(cardRepository, never()).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t43 한글 부분일치 검색어는 도감번호 매핑 후 findByNationalPokedexNumbersIn으로 카드를 조회한다")
    void t43() {
        Pageable pageable = PageRequest.of(0, 20);
        PokedexKoName pikachu = PokedexKoName.builder()
                .pokedexNumber(25)
                .nameKo("피카츄")
                .nameKoChosung("ㅍㅋㅊ")
                .build();
        Card card = Card.builder().id(1L).name("Pikachu").nationalPokedexNumbers(List.of(25)).build();
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(pokedexKoNameRepository.findByNameKoContaining("피카")).willReturn(List.of(pikachu));
        given(cardRepository.searchByPokedexNumbersOrderByName(List.of(25), false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("피카", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Pikachu");
        assertThat(result.getContent().get(0).fuzzyMatch()).isFalse();
        verify(cardRepository).searchByPokedexNumbersOrderByName(List.of(25), false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable);
    }

    @Test
    @DisplayName("t44 검색어가 자음(초성)으로만 이뤄지면 findByNameKoChosungContaining으로 조회한다")
    void t44() {
        Pageable pageable = PageRequest.of(0, 20);
        PokedexKoName pikachu = PokedexKoName.builder()
                .pokedexNumber(25)
                .nameKo("피카츄")
                .nameKoChosung("ㅍㅋㅊ")
                .build();
        Card card = Card.builder().id(1L).name("Pikachu").nationalPokedexNumbers(List.of(25)).build();
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(pokedexKoNameRepository.findByNameKoChosungContaining("ㅍㅋㅊ")).willReturn(List.of(pikachu));
        given(cardRepository.searchByPokedexNumbersOrderByName(List.of(25), false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("ㅍㅋㅊ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(pokedexKoNameRepository).findByNameKoChosungContaining("ㅍㅋㅊ");
        verify(pokedexKoNameRepository, never()).findByNameKoContaining(any());
    }

    @Test
    @DisplayName("t45 매핑되는 도감명이 없으면 빈 페이지를 반환하고 카드 리포지토리는 호출하지 않는다")
    void t45() {
        Pageable pageable = PageRequest.of(0, 20);
        given(pokedexKoNameRepository.findByNameKoContaining("존재안하는한글이름")).willReturn(List.of());
        // #187: 정확 검색이 0건이면 유사도 폴백을 시도한다 - 폴백도 0건이어야 최종 결과가 비게 된다.
        given(pokedexKoNameRepository.findByNameKoSimilarTo("존재안하는한글이름", 0.14)).willReturn(List.of());

        Page<CardResponse> result = cardQueryService.searchByKeyword("존재안하는한글이름", pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(cardRepository, never()).findByNationalPokedexNumbersIn(any(), any());
    }

    // ===== #187: 오타 허용(유사도) 검색 폴백 =====

    @Test
    @DisplayName("t51 영문 정확 검색이 0건이고 키워드가 2글자 이상이면 유사도 검색으로 폴백한다")
    void t51() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        Card charizard = Card.builder().id(1L).name("Charizard").build();
        Page<Card> similarPage = new PageImpl<>(List.of(charizard), pageable, 1);
        given(cardRepository.searchByNameOrderByName("Charizrd", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(emptyPage);
        given(cardRepository.existsByNameContainingIgnoreCase("Charizrd")).willReturn(false);
        given(cardRepository.searchByNameSimilarToWithFilters("Charizrd", 0.14, false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(similarPage);

        Page<CardResponse> result = cardQueryService.searchByKeyword("Charizrd", pageable);

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(true);
        verify(cardRepository).searchByNameSimilarToWithFilters("Charizrd", 0.14, false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable);
    }

    @Test
    @DisplayName("t52 영문 정확 검색 결과가 있으면 유사도 검색으로 폴백하지 않는다(회귀 확인)")
    void t52() {
        Pageable pageable = PageRequest.of(0, 20);
        Card charizard = Card.builder().id(1L).name("Charizard").build();
        Page<Card> page = new PageImpl<>(List.of(charizard), pageable, 1);
        given(cardRepository.searchByNameOrderByName("char", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("char", pageable);

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(false);
        verify(cardRepository, never()).searchByNameSimilarToWithFilters(any(), anyDouble(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("t48 영문 키워드가 1글자면 정확 검색이 0건이어도 유사도 검색을 생략한다")
    void t48() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.searchByNameOrderByName("c", false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(emptyPage);

        Page<CardResponse> result = cardQueryService.searchByKeyword("c", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(cardRepository, never()).searchByNameSimilarToWithFilters(any(), anyDouble(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("t49 한글 정확 검색이 0건이고 키워드가 2글자 이상이면 유사도 검색으로 폴백한다")
    void t49() {
        Pageable pageable = PageRequest.of(0, 20);
        PokedexKoName charizard = PokedexKoName.builder()
                .pokedexNumber(6).nameKo("리자몽").nameKoChosung("ㄹㅈㅁ").build();
        PokedexKoName charmeleon = PokedexKoName.builder()
                .pokedexNumber(5).nameKo("리자드").nameKoChosung("ㄹㅈㄷ").build();
        Card charizardCard = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        Page<Card> page = new PageImpl<>(List.of(charizardCard), pageable, 1);
        given(pokedexKoNameRepository.findByNameKoContaining("리자옹")).willReturn(List.of());
        given(pokedexKoNameRepository.findByNameKoSimilarTo("리자옹", 0.14))
                .willReturn(List.of(charizard, charmeleon));
        given(cardRepository.searchByPokedexNumbersOrderByName(List.of(6, 5), false, NO_TYPES, false, NO_RARITIES, false, NO_LANGUAGES, false, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardQueryService.searchByKeyword("리자옹", pageable);

        assertThat(result.getContent()).extracting(CardResponse::name).containsExactly("Charizard");
        assertThat(result.getContent()).extracting(CardResponse::fuzzyMatch).containsExactly(true);
        verify(pokedexKoNameRepository).findByNameKoSimilarTo("리자옹", 0.14);
    }

    @Test
    @DisplayName("t50 한글 키워드가 1글자면 정확 검색이 0건이어도 유사도 검색을 생략한다")
    void t50() {
        Pageable pageable = PageRequest.of(0, 20);
        given(pokedexKoNameRepository.findByNameKoContaining("리")).willReturn(List.of());

        Page<CardResponse> result = cardQueryService.searchByKeyword("리", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(pokedexKoNameRepository, never()).findByNameKoSimilarTo(any(), any(Double.class));
    }

    @Test
    @DisplayName("t7 포켓몬 카드(도감번호 있음)는 도감번호 겹침 기준으로 유사 카드를 조회한다")
    void t7() {
        Card card = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        Card related = Card.builder().id(2L).name("Charizard ex").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByPokedexNumber(1L)).willReturn(List.of(related));

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Charizard ex");
        verify(cardRepository, never()).findRelatedByExpansion(any(), any());
    }

    @Test
    @DisplayName("t8 포켓몬 카드인데 도감번호가 겹치는 카드가 없으면 빈 목록을 반환한다")
    void t8() {
        Card card = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByPokedexNumber(1L)).willReturn(List.of());

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t9 트레이너 카드(도감번호 없음)는 같은 세트 기준으로 유사 카드를 조회한다")
    void t9() {
        Expansion expansion = Expansion.builder().id("base1").name("Base").syncedAt(LocalDateTime.now()).build();
        Card card = Card.builder().id(1L).name("Professor's Research").expansion(expansion).build();
        Card related = Card.builder().id(2L).name("Charizard").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByExpansion("base1", 1L)).willReturn(List.of(related));

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Charizard");
        verify(cardRepository, never()).findRelatedByPokedexNumber(any());
    }

    @Test
    @DisplayName("t10 트레이너 카드인데 같은 세트에 다른 카드가 없으면 빈 목록을 반환한다")
    void t10() {
        Expansion expansion = Expansion.builder().id("swsh1").name("Sword & Shield").syncedAt(LocalDateTime.now()).build();
        Card card = Card.builder().id(1L).name("Quick Ball").expansion(expansion).build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByExpansion("swsh1", 1L)).willReturn(List.of());

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t12 트레이너 카드인데 세트 정보(expansion)가 없으면 빈 목록을 반환한다")
    void t12() {
        Card card = Card.builder().id(1L).name("Professor's Research").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).isEmpty();
        verify(cardRepository, never()).findRelatedByPokedexNumber(any());
        verify(cardRepository, never()).findRelatedByExpansion(any(), any());
    }

    @Test
    @DisplayName("t39 유사 카드 결과들의 id를 모아 등급을 배치 조회하고 카드별 등급 배열로 매핑한다")
    void t39() {
        Card card = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        Card related1 = Card.builder().id(2L).name("Charizard ex").build();
        Card related2 = Card.builder().id(3L).name("Charizard V").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByPokedexNumber(1L)).willReturn(List.of(related1, related2));
        given(cardRepository.findGradesByCardIds(eq(List.of(2L, 3L)), any())).willReturn(List.of(
                gradeView(2L, "A")
        ));

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result.get(0).grades()).containsExactly("A");
        assertThat(result.get(1).grades()).isEmpty();
        verify(cardRepository, times(1)).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t40 유사 카드 결과가 비어 있으면 등급 배치 조회를 호출하지 않는다")
    void t40() {
        Card card = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByPokedexNumber(1L)).willReturn(List.of());

        List<CardResponse> result = cardQueryService.getRelated(1L);

        assertThat(result).isEmpty();
        verify(cardRepository, never()).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t11 존재하지 않는 id로 유사 카드를 조회하면 CARD_NOT_FOUND 예외가 발생한다")
    void t11() {
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardQueryService.getRelated(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }

    @Test
    @DisplayName("t15 존재하는 external_id로 조회하면 카드를 반환한다")
    void t15() {
        Card card = Card.builder().id(1L).name("Mew ex").externalId("sv3pt5-151").build();
        given(cardRepository.findByExternalId("sv3pt5-151")).willReturn(Optional.of(card));

        Optional<Card> result = cardQueryService.findByExternalId("sv3pt5-151");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Mew ex");
    }

    @Test
    @DisplayName("t16 존재하지 않는 external_id로 조회하면 빈 Optional을 반환한다")
    void t16() {
        given(cardRepository.findByExternalId("does-not-exist")).willReturn(Optional.empty());

        Optional<Card> result = cardQueryService.findByExternalId("does-not-exist");

        assertThat(result).isEmpty();
    }
}
