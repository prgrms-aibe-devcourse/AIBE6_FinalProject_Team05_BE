package com.pokade.domain.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

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
import com.pokade.global.security.TokenBlacklistStore;

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

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @Test
    @DisplayName("t1 쿼리 파라미터로 카드를 검색하면 200과 페이지 결과를 반환한다")
    void t1() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(isNull(), eq(List.of("Fire")), eq(List.of("Rare Holo")), isNull(), eq("base1"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        var result = mockMvcTester.get()
                .uri("/api/cards?types=Fire&rarity=Rare Holo&expansionId=base1")
                .assertThat()
                .hasStatusOk();
        result.bodyJson().extractingPath("$.data.content[0].name").isEqualTo("Charizard");
        result.bodyJson().extractingPath("$.data.content[0].grades").asList().isEmpty();
    }

    @Test
    @DisplayName("t27 languages 쿼리 파라미터를 서비스에 그대로 위임한다(#263)")
    void t27() {
        CardResponse card = new CardResponse(1L, "sv10_ja-1", "クヌギダマ", "피콘", "JA", "サンダー", "Common", "Pokémon",
                List.of("Grass"), null, null, "sv10_ja", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(isNull(), isNull(), isNull(), eq(List.of("EN", "JA")), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards?languages=EN,JA")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].languageCode").isEqualTo("JA");
    }

    @Test
    @DisplayName("t11 types를 콤마로, rarity를 반복 파라미터로 넘기면 둘 다 다중 값 목록으로 위임한다")
    void t11() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardService.search(
                isNull(), eq(List.of("Fire", "Water")), eq(List.of("Common", "Rare Holo")), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
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
        given(cardService.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @DisplayName("t12 sort 쿼리 파라미터를 서비스에 그대로 위임한다")
    void t12() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("name"), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards?sort=name")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t15 size가 상한을 초과하면 서비스의 INVALID_INPUT 예외가 400으로 응답된다")
    void t15() {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "size는 최대 100까지 요청할 수 있습니다."))
                .given(cardService).search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));

        mockMvcTester.get()
                .uri("/api/cards?size=101")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t16 types 개수가 상한을 초과하면 서비스의 INVALID_INPUT 예외가 400으로 응답된다")
    void t16() {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "types는 최대 20개까지 지정할 수 있습니다."))
                .given(cardService).search(isNull(), any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));

        String query = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "types=type" + i)
                .reduce((a, b) -> a + "&" + b)
                .orElseThrow();

        mockMvcTester.get()
                .uri("/api/cards?" + query)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t20 minPrice/maxPrice 쿼리 파라미터를 서비스에 그대로 위임한다")
    void t20() {
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(10000), eq(50000), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards?minPrice=10000&maxPrice=50000")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("Charizard");
    }

    @Test
    @DisplayName("t21 minPrice가 maxPrice보다 크면 서비스의 INVALID_INPUT 예외가 400으로 응답된다")
    void t21() {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "minPrice는 maxPrice보다 클 수 없습니다."))
                .given(cardService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(50000), eq(10000), isNull(), any(Pageable.class));

        mockMvcTester.get()
                .uri("/api/cards?minPrice=50000&maxPrice=10000")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t17 검색어가 길이 상한을 초과하면 서비스의 INVALID_INPUT 예외가 400으로 응답된다")
    void t17() {
        String tooLongKeyword = "a".repeat(101);
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "검색어는 최대 100자까지 입력할 수 있습니다."))
                .given(cardService).searchByKeyword(eq(tooLongKeyword), any(Pageable.class));

        mockMvcTester.get()
                .uri("/api/cards/search?q=" + tooLongKeyword)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t3 존재하는 카드 id로 상세조회하면 200과 확장팩·변형 정보를 포함한 응답을 반환한다")
    void t3() {
        CardDetailResponse.ExpansionSummary expansion = new CardDetailResponse.ExpansionSummary(
                "base1", "Base", "Base", "BS", 102, LocalDate.of(1999, 1, 9), null, null);
        CardDetailResponse.VariantSummary variant = new CardDetailResponse.VariantSummary(
                1L, "unlimitedHolofoil", true, null, null, List.of("S", "A"));
        CardDetailResponse detail = new CardDetailResponse(
                1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), "Mitsuhiro Arita", "4/102", null, null, null, 15,
                expansion, List.of(variant));
        given(cardService.getDetail(1L)).willReturn(detail);

        var result = mockMvcTester.get()
                .uri("/api/cards/1")
                .assertThat()
                .hasStatusOk();
        result.bodyJson().extractingPath("$.data.name").isEqualTo("Charizard");
        result.bodyJson().extractingPath("$.data.expansion.id").isEqualTo("base1");
        result.bodyJson().extractingPath("$.data.variants[0].variantName").isEqualTo("unlimitedHolofoil");
        result.bodyJson().extractingPath("$.data.variants[0].grades").asList().containsExactly("S", "A");
        result.bodyJson().extractingPath("$.data.viewCount").isEqualTo(15);
    }

    @Test
    @DisplayName("t5 카드 상세조회 응답은 status/code/msg/data 형태의 ApiResponse 구조를 따른다")
    void t5() {
        CardDetailResponse.ExpansionSummary expansion = new CardDetailResponse.ExpansionSummary(
                "base1", "Base", "Base", "BS", 102, LocalDate.of(1999, 1, 9), null, null);
        CardDetailResponse detail = new CardDetailResponse(
                1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), "Mitsuhiro Arita", "4/102", null, null, null, 0,
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
        CardResponse card = new CardResponse(1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, "base1", List.of(), false);
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
        CardResponse related = new CardResponse(2L, "sv3pt5-6", "Charizard ex", null, "EN", "151", "Double Rare", "Pokémon",
                List.of("Fire"), null, null, "sv3pt5", List.of(), false);
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

    @Test
    @DisplayName("t22 숫자가 아닌 카드 id로 상세조회하면 400과 일반화된 메시지를 반환하고 내부 예외 정보를 노출하지 않는다")
    void t22() {
        var result = mockMvcTester.get()
                .uri("/api/cards/abc")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST);
        result.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        result.bodyJson().extractingPath("$.msg").isEqualTo("잘못된 요청 형식입니다.");
        result.bodyText().doesNotContain("MethodArgumentTypeMismatchException", "java.lang.Long", "NumberFormatException");
    }

    @Test
    @DisplayName("t23 숫자가 아닌 카드 id로 유사 카드를 조회하면 400과 일반화된 메시지를 반환한다")
    void t23() {
        mockMvcTester.get()
                .uri("/api/cards/abc/related")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("t24 한글 검색어로 요청하면 서비스에 그대로 위임하고 200과 페이지 결과를 반환한다")
    void t24() {
        CardResponse card = new CardResponse(1L, "sv3pt5-25", "피카츄", null, "JA", "151", "Common", "Pokémon",
                List.of("Lightning"), null, null, "sv3pt5", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.searchByKeyword(eq("피카츄"), any(Pageable.class))).willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards/search?q=피카츄")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("피카츄");
        verify(cardService).searchByKeyword(eq("피카츄"), any(Pageable.class));
    }

    @Test
    @DisplayName("t25 초성 검색어도 판별 없이 서비스에 그대로 위임한다")
    void t25() {
        CardResponse card = new CardResponse(1L, "sv3pt5-25", "피카츄", null, "JA", "151", "Common", "Pokémon",
                List.of("Lightning"), null, null, "sv3pt5", List.of(), false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(card), pageable, 1);
        given(cardService.searchByKeyword(eq("ㅍㅋㅊ"), any(Pageable.class))).willReturn(page);

        mockMvcTester.get()
                .uri("/api/cards/search?q=ㅍㅋㅊ")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content[0].name").isEqualTo("피카츄");
        verify(cardService).searchByKeyword(eq("ㅍㅋㅊ"), any(Pageable.class));
    }

    @Test
    @DisplayName("t26 매칭되는 카드가 없으면 200과 빈 목록을 반환한다")
    void t26() {
        Pageable pageable = PageRequest.of(0, 20);
        given(cardService.searchByKeyword(eq("존재안하는이름"), any(Pageable.class))).willReturn(Page.empty(pageable));

        mockMvcTester.get()
                .uri("/api/cards/search?q=존재안하는이름")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.content").isEmpty();
    }
}
