package com.pokade.domain.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;

@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private CardService cardService;

    @Test
    @DisplayName("t1 쿼리 파라미터로 카드를 검색하면 200과 페이지 결과를 반환한다")
    void t1() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1");
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(eq("char"), eq("Fire"), eq("Rare Holo"), eq("base1"), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/cards?name=char&types=Fire&rarity=Rare Holo&expansionId=base1")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t2 쿼리 파라미터가 없으면 전체 조건을 null로 위임한다")
    void t2() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/cards")
                .assertThat()
                .hasStatusOk();
    }
}