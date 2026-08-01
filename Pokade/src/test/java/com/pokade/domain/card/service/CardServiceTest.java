package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

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
        given(cardRepository.search(List.of("Fire"), List.of("Rare Holo"), null, "base1", null, null, "name", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(List.of("Fire"), List.of("Rare Holo"), null, "base1", null, null, "name", pageable);

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
        given(cardRepository.search(List.of("Fire", "Water"), List.of("Common", "Rare Holo"), null, null, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardService.search(
                List.of("Fire", "Water"), List.of("Common", "Rare Holo"), null, null, null, null, null, pageable);

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
        given(cardRepository.search(null, null, null, null, null, null, "latest", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, "latest", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t17 size가 100 이하이면 정상 처리된다")
    void t17() {
        Pageable pageable = PageRequest.of(0, 100);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(null, null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t18 size가 100을 초과하면 INVALID_INPUT 예외가 발생한다")
    void t18() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> cardService.search(null, null, null, null, null, null, null, pageable))
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

        assertThatThrownBy(() -> cardService.search(tooManyTypes, null, null, null, null, null, null, pageable))
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

        assertThatThrownBy(() -> cardService.search(null, tooManyRarities, null, null, null, null, null, pageable))
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
        given(cardRepository.search(exactlyTwenty, null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(exactlyTwenty, null, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("t31 grades가 20개를 초과하면 INVALID_INPUT 예외가 발생한다")
    void t31() {
        Pageable pageable = PageRequest.of(0, 20);
        List<String> tooManyGrades = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "S")
                .toList();

        assertThatThrownBy(() -> cardService.search(null, null, tooManyGrades, null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t32 grades에 S/A/B 화이트리스트 외 값이 오면 INVALID_INPUT 예외가 발생한다")
    void t32() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.search(null, null, List.of("PSA10"), null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t33 grades에 임의 문자열이 오면 INVALID_INPUT 예외가 발생한다")
    void t33() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.search(null, null, List.of("아무값"), null, null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("t34 grades가 S/A/B로만 구성되면 리포지토리에 그대로 위임한다")
    void t34() {
        Card card = Card.builder()
                .id(1L)
                .name("Charizard")
                .types(List.of("Fire"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardRepository.search(null, null, List.of("S", "A", "B"), null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, List.of("S", "A", "B"), null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t41 minPrice가 maxPrice보다 크면 INVALID_INPUT 예외가 발생한다")
    void t41() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> cardService.search(null, null, null, null, 20000, 10000, null, pageable))
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
        given(cardRepository.search(null, null, null, null, 10000, 50000, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, null, 10000, 50000, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t35 검색 결과 카드들의 id를 모아 등급을 배치 조회하고 카드별 등급 배열로 매핑한다")
    void t35() {
        Card charizard = Card.builder().id(1L).name("Charizard").types(List.of("Fire")).build();
        Card blastoise = Card.builder().id(2L).name("Blastoise").types(List.of("Water")).build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(charizard, blastoise), pageable, 2);
        given(cardRepository.search(null, null, null, null, null, null, null, pageable)).willReturn(page);
        given(cardRepository.findGradesByCardIds(eq(List.of(1L, 2L)), any())).willReturn(List.of(
                gradeView(1L, "B"),
                gradeView(1L, "S"),
                gradeView(1L, "A")
        ));

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, null, pageable);

        assertThat(result.getContent().get(0).grades()).containsExactly("S", "A", "B");
        assertThat(result.getContent().get(1).grades()).isEmpty();
        verify(cardRepository, times(1)).findGradesByCardIds(any(), any());
    }

    @Test
    @DisplayName("t36 검색 결과가 비어 있으면 등급 배치 조회를 호출하지 않는다")
    void t36() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Card> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardRepository.search(null, null, null, null, null, null, null, pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, null, null, null, null, pageable);

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
}
