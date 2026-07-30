package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
        given(cardRepository.search(List.of("Fire"), List.of("Rare Holo"), "base1", "name", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(List.of("Fire"), List.of("Rare Holo"), "base1", "name", pageable);

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
        given(cardRepository.search(List.of("Fire", "Water"), List.of("Common", "Rare Holo"), null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardService.search(
                List.of("Fire", "Water"), List.of("Common", "Rare Holo"), null, null, pageable);

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
        given(cardRepository.search(null, null, null, "latest", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, null, "latest", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Charizard");
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

        CardDetailResponse result = cardService.getDetail(1L);

        assertThat(result.name()).isEqualTo("Charizard");
        assertThat(result.expansion().id()).isEqualTo("base1");
        assertThat(result.variants()).hasSize(2);
        assertThat(result.variants().get(0).variantName()).isEqualTo("unlimitedHolofoil");
    }

    @Test
    @DisplayName("t3 존재하지 않는 id로 상세조회하면 CARD_NOT_FOUND 예외가 발생한다")
    void t3() {
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
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
    @DisplayName("t11 존재하지 않는 id로 유사 카드를 조회하면 CARD_NOT_FOUND 예외가 발생한다")
    void t11() {
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getRelated(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }
}
