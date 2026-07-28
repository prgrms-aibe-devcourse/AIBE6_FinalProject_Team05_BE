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
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;

@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("t1 쿼리 파라미터로 카드를 검색하면 200과 페이지 결과를 반환한다")
    void t1() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1");
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(eq("Fire"), eq("Rare Holo"), eq("base1"), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards?types=Fire&rarity=Rare Holo&expansionId=base1")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t11 types를 콤마로, rarity를 반복 파라미터로 넘기면 둘 다 다중 값 목록으로 위임한다")
    void t11() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardService.search(
                eq(List.of("Fire", "Water")), eq(List.of("Common", "Rare Holo")), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards?types=Fire,Water&rarity=Common&rarity=Rare Holo")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @DisplayName("t2 쿼리 파라미터가 없으면 전체 조건을 null로 위임한다")
    void t2() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardService.search(isNull(), isNull(), isNull(), any(Pageable.class)))
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

    @Test
    @DisplayName("t6 검색어로 카드 이름 키워드 검색을 하면 200과 페이지 결과를 반환한다")
    void t6() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1");
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.searchByKeyword(eq("char"), any(Pageable.class))).willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards/search?q=char")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t7 검색어 없이 키워드 검색을 요청하면 400을 반환한다")
    void t7() {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT))
                .given(cardService).searchByKeyword(eq(null), any(Pageable.class));

        mockMvcTester.get()
                .uri("/api/cards/search")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t8 존재하는 카드 id로 유사 카드를 조회하면 200과 목록을 반환한다")
    void t8() {
        CardResponse related = new CardResponse(2L, "sv3pt5-6", "Charizard ex", "151", "Double Rare", "Pokémon",
                List.of("Fire"), null, null, "sv3pt5");
        given(cardService.getRelated(1L)).willReturn(List.of(related));

        mockMvcTester.get()
                .uri("/api/cards/1/related")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data[0].name").isEqualTo("Charizard ex");
    }

    @Test
    @DisplayName("t9 유사 카드가 없으면 200과 빈 목록을 반환한다")
    void t9() {
        given(cardService.getRelated(1L)).willReturn(List.of());

        mockMvcTester.get()
                .uri("/api/cards/1/related")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data").isEmpty();
    }

    @Test
    @DisplayName("t10 존재하지 않는 카드 id로 유사 카드를 조회하면 404를 반환한다")
    void t10() {
        willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND)).given(cardService).getRelated(999L);

        mockMvcTester.get()
                .uri("/api/cards/999/related")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("CARD_NOT_FOUND");
    }
}
