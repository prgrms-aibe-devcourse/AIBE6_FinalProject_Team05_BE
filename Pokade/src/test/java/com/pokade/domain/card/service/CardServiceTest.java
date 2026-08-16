package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
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
import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
import com.pokade.domain.card.repository.PokedexKoNameRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private PokedexKoNameRepository pokedexKoNameRepository;

    @Mock
    private ExpansionRepository expansionRepository;

    @Mock
    private CardNameKoResolver cardNameKoResolver;

    @InjectMocks
    private CardService cardService;

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

        Page<CardResponse> result = cardService.search(List.of("Fire"), List.of("Rare Holo"), "base1", null, null, "name", pageable);

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

        Page<CardResponse> result = cardService.search(
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

        Page<CardResponse> result = cardService.search(null, null, null, null, null, "latest", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t17 size가 100 이하이면 정상 처리된다")
    void t17() {
        Pageable pageable = PageRequest.of(0, 100);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t18 size가 100을 초과하면 INVALID_INPUT 예외가 발생한다")
    void t18() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> cardService.search(null, null, null, null, null, null, pageable))
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

        assertThatThrownBy(() -> cardService.search(tooManyTypes, null, null, null, null, null, pageable))
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

        assertThatThrownBy(() -> cardService.search(null, tooManyRarities, null, null, null, null, pageable))
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

        Page<CardResponse> result = cardService.search(exactlyTwenty, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t41 minPrice가 maxPrice보다 크면 INVALID_INPUT 예외가 발생한다")
    void t41() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.search(null, null, null, 20000, 10000, null, pageable))
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

        Page<CardResponse> result = cardService.search(null, null, null, 10000, 50000, null, pageable);

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

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, pageable);

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

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, pageable);

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

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, pageable);

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

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, pageable);

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

        CardDetailResponse result = cardService.getDetail(1L);

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

        assertThatThrownBy(() -> cardService.getDetail(999L))
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
        given(cardRepository.findByNameContainingIgnoreCase("char", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword("char", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t5 검색어가 없으면 INVALID_INPUT 예외가 발생한다")
    void t5() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.searchByKeyword(null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t6 검색어가 공백뿐이면 INVALID_INPUT 예외가 발생한다")
    void t6() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.searchByKeyword("   ", pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t22 검색어가 100자를 초과하면 INVALID_INPUT 예외가 발생한다")
    void t22() {
        Pageable pageable = PageRequest.of(0, 20);
        String tooLongKeyword = "a".repeat(101);

        assertThatThrownBy(() -> cardService.searchByKeyword(tooLongKeyword, pageable))
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
        given(cardRepository.findByNameContainingIgnoreCase(exactlyHundred, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword(exactlyHundred, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("t24 키워드 검색에서 size가 100을 초과하면 INVALID_INPUT 예외가 발생한다")
    void t24() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> cardService.searchByKeyword("char", pageable))
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
        given(cardRepository.findByNameContainingIgnoreCase("char", pageable)).willReturn(page);
        given(cardRepository.findGradesByCardIds(eq(List.of(1L, 2L)), any())).willReturn(List.of(
                gradeView(1L, "B"),
                gradeView(1L, "S")
        ));

        Page<CardResponse> result = cardService.searchByKeyword("char", pageable);

        assertThat(result.getContent().get(0).grades()).containsExactly("S", "B");
        assertThat(result.getContent().get(1).grades()).isEmpty();
        verify(cardRepository, times(1)).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t38 키워드 검색 결과가 비어 있으면 등급 배치 조회를 호출하지 않는다")
    void t38() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.findByNameContainingIgnoreCase("char", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword("char", pageable);

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
        given(cardRepository.findByNationalPokedexNumbersIn(List.of(25), pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword("피카", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Pikachu");
        verify(cardRepository).findByNationalPokedexNumbersIn(List.of(25), pageable);
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
        given(cardRepository.findByNationalPokedexNumbersIn(List.of(25), pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword("ㅍㅋㅊ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(pokedexKoNameRepository).findByNameKoChosungContaining("ㅍㅋㅊ");
        verify(pokedexKoNameRepository, never()).findByNameKoContaining(any());
    }

    @Test
    @DisplayName("t45 매핑되는 도감명이 없으면 빈 페이지를 반환하고 카드 리포지토리는 호출하지 않는다")
    void t45() {
        Pageable pageable = PageRequest.of(0, 20);
        given(pokedexKoNameRepository.findByNameKoContaining("존재안하는한글이름")).willReturn(List.of());

        Page<CardResponse> result = cardService.searchByKeyword("존재안하는한글이름", pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(cardRepository, never()).findByNationalPokedexNumbersIn(any(), any());
    }

    @Test
    @DisplayName("t7 포켓몬 카드(도감번호 있음)는 도감번호 겹침 기준으로 유사 카드를 조회한다")
    void t7() {
        Card card = Card.builder().id(1L).name("Charizard").nationalPokedexNumbers(List.of(6)).build();
        Card related = Card.builder().id(2L).name("Charizard ex").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardRepository.findRelatedByPokedexNumber(1L)).willReturn(List.of(related));

        List<CardResponse> result = cardService.getRelated(1L);

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

        List<CardResponse> result = cardService.getRelated(1L);

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

        List<CardResponse> result = cardService.getRelated(1L);

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

        List<CardResponse> result = cardService.getRelated(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t12 트레이너 카드인데 세트 정보(expansion)가 없으면 빈 목록을 반환한다")
    void t12() {
        Card card = Card.builder().id(1L).name("Professor's Research").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));

        List<CardResponse> result = cardService.getRelated(1L);

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

        List<CardResponse> result = cardService.getRelated(1L);

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

        List<CardResponse> result = cardService.getRelated(1L);

        assertThat(result).isEmpty();
        verify(cardRepository, never()).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t11 존재하지 않는 id로 유사 카드를 조회하면 CARD_NOT_FOUND 예외가 발생한다")
    void t11() {
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getRelated(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }

    @Test
    @DisplayName("t15 존재하는 external_id로 조회하면 카드를 반환한다")
    void t15() {
        Card card = Card.builder().id(1L).name("Mew ex").externalId("sv3pt5-151").build();
        given(cardRepository.findByExternalId("sv3pt5-151")).willReturn(Optional.of(card));

        Optional<Card> result = cardService.findByExternalId("sv3pt5-151");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Mew ex");
    }

    @Test
    @DisplayName("t16 존재하지 않는 external_id로 조회하면 빈 Optional을 반환한다")
    void t16() {
        given(cardRepository.findByExternalId("does-not-exist")).willReturn(Optional.empty());

        Optional<Card> result = cardService.findByExternalId("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t48 매핑에 없는 rarity_code는 원본 rarity 텍스트로 폴백해서 노출된다")
    void t48() {
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of(rarityView("ZZ", "Special Art Rare")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.rarities()).containsExactly("Special Art Rare");
    }

    @Test
    @DisplayName("t49 rarity_code가 null인 카드도 원본 rarity 텍스트로 Facet에 노출된다")
    void t49() {
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of(rarityView(null, "プロモ")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.rarities()).containsExactly("プロモ");
    }

    @Test
    @DisplayName("t50 name이 null인 expansion이 있어도 NPE 없이 빈 문자열로 노출된다")
    void t50() {
        Expansion expansion = Expansion.builder().id("legacy1").name(null).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions()).hasSize(1);
        assertThat(result.expansions().get(0).id()).isEqualTo("legacy1");
        assertThat(result.expansions().get(0).name()).isEqualTo("");
    }

    @Test
    @DisplayName("t51 name이 null인 expansion과 정상 expansion이 섞여 있어도 나머지는 이름순 정렬이 유지된다")
    void t51() {
        Expansion legacy = Expansion.builder().id("legacy1").name(null).syncedAt(LocalDateTime.now()).build();
        Expansion base = Expansion.builder().id("base1").name("Base").syncedAt(LocalDateTime.now()).build();
        Expansion swsh = Expansion.builder().id("swsh1").name("Sword & Shield").syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(swsh, legacy, base));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("", "Base", "Sword & Shield");
    }

    @Test
    @DisplayName("t52 rarity_code와 rarity가 둘 다 null인 카드가 섞여 있어도 NPE 없이 나머지 rarity는 정상 노출된다")
    void t52() {
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of(
                rarityView(null, null),
                rarityView("C", "Common"),
                rarityView(null, "프로모")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.rarities()).containsExactlyInAnyOrder("Common", "프로모");
    }

    @Test
    @DisplayName("t53 series가 있는 expansion은 series 값이 그대로 Facet에 노출된다")
    void t53() {
        Expansion expansion = Expansion.builder().id("sv3pt5").name("151")
                .series("Scarlet & Violet").syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions().get(0).series()).isEqualTo("Scarlet & Violet");
    }

    @Test
    @DisplayName("t54 series가 null인 expansion은 \"기타\" 그룹으로 노출된다")
    void t54() {
        Expansion expansion = Expansion.builder().id("legacy1").name("Legacy")
                .series(null).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions().get(0).series()).isEqualTo("기타");
    }

    @Test
    @DisplayName("t55 series 그룹은 그룹 내 최신 release_date 기준 내림차순으로, 그룹 내에서는 이름순으로 정렬된다")
    void t55() {
        // Old Series의 최신 release_date(2016)보다 New Series의 release_date(2020)가 더 최신이므로
        // New Series 그룹 전체가 앞에 와야 한다.
        Expansion oldSeriesNewer = Expansion.builder().id("old2").name("Old B")
                .series("Old Series").releaseDate(LocalDate.of(2016, 1, 1)).syncedAt(LocalDateTime.now()).build();
        Expansion oldSeriesOlder = Expansion.builder().id("old1").name("Old A")
                .series("Old Series").releaseDate(LocalDate.of(2015, 1, 1)).syncedAt(LocalDateTime.now()).build();
        Expansion newSeries = Expansion.builder().id("new1").name("New A")
                .series("New Series").releaseDate(LocalDate.of(2020, 1, 1)).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(oldSeriesNewer, oldSeriesOlder, newSeries));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("New A", "Old A", "Old B");
    }

    @Test
    @DisplayName("t56 release_date가 전부 null인 series는 가장 오래된 것으로 취급돼 맨 뒤로 정렬된다")
    void t56() {
        Expansion noDateSeries = Expansion.builder().id("nodate1").name("No Date")
                .series("Unknown Timing").syncedAt(LocalDateTime.now()).build();
        Expansion datedSeries = Expansion.builder().id("dated1").name("Dated")
                .series("Dated Series").releaseDate(LocalDate.of(1999, 1, 1)).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findDistinctTypes()).willReturn(List.of());
        given(cardRepository.findDistinctRarityCodes()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(noDateSeries, datedSeries));

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("Dated", "No Date");
    }

    private CardRepository.CardRarityView rarityView(String rarityCode, String rarity) {
        return new CardRepository.CardRarityView() {
            @Override
            public String getRarityCode() {
                return rarityCode;
            }

            @Override
            public String getRarity() {
                return rarity;
            }
        };
    }
}
