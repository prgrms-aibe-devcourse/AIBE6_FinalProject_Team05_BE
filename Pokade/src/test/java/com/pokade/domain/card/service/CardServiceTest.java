package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
        given(cardRepository.search("char", "Fire", "Rare Holo", "base1", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.search("char", "Fire", "Rare Holo", "base1", pageable);

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
}