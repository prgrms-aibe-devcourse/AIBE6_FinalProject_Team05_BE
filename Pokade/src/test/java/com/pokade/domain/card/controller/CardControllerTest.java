package com.pokade.domain.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDate;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

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
                .uri("/api/cards?name=char&types=Fire&rarity=Rare Holo&expansionId=base1")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t2 쿼리 파라미터가 없으면 전체 조건을 null로 위임한다")
    void t2() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardService.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @DisplayName("t3 존재하는 카드 id로 상세조회하면 200과 확장팩·변형 정보를 포함한 응답을 반환한다")
    void t3() {
        CardDetailResponse.ExpansionSummary expansion = new CardDetailResponse.ExpansionSummary(
                "base1", "Base", "Base", "BS", 102, LocalDate.of(1999, 1, 9), null, null);
        CardDetailResponse.VariantSummary variant = new CardDetailResponse.VariantSummary(
                1L, "unlimitedHolofoil", true, null, null);
        CardDetailResponse detail = new CardDetailResponse(
                1L, "base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), "Mitsuhiro Arita", "4/102", null, null, null,
                expansion, List.of(variant));
        given(cardService.getDetail(1L)).willReturn(detail);

        var result = mockMvcTester.get()
                .uri("/api/cards/1")
                .assertThat()
                .hasStatusOk();
        result.bodyJson().extractingPath("$.data.name").isEqualTo("Charizard");
        result.bodyJson().extractingPath("$.data.expansion.id").isEqualTo("base1");
        result.bodyJson().extractingPath("$.data.variants[0].variantName").isEqualTo("unlimitedHolofoil");
    }

    @Test
    @DisplayName("t5 카드 상세조회 응답은 status/code/msg/data 형태의 ApiResponse 구조를 따른다")
    void t5() {
        CardDetailResponse.ExpansionSummary expansion = new CardDetailResponse.ExpansionSummary(
                "base1", "Base", "Base", "BS", 102, LocalDate.of(1999, 1, 9), null, null);
        CardDetailResponse detail = new CardDetailResponse(
                1L, "base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), "Mitsuhiro Arita", "4/102", null, null, null,
                expansion, List.of());
        given(cardService.getDetail(1L)).willReturn(detail);

        var result = mockMvcTester.get()
                .uri("/api/cards/1")
                .assertThat()
                .hasStatusOk();
        result.bodyJson().extractingPath("$.status").isEqualTo(200);
        result.bodyJson().extractingPath("$.code").isEqualTo("OK");
        result.bodyJson().extractingPath("$.msg").isNotNull();
        result.bodyJson().extractingPath("$.data.name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t4 존재하지 않는 카드 id로 상세조회하면 404를 반환한다")
    void t4() {
        willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND)).given(cardService).getDetail(999L);

        mockMvcTester.get()
                .uri("/api/cards/999")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("CARD_NOT_FOUND");
    }
}
