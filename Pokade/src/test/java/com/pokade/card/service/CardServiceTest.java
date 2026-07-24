package com.pokade.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

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

import com.pokade.card.dto.CardResponse;
import com.pokade.card.entity.Card;
import com.pokade.card.repository.CardRepository;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

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
}