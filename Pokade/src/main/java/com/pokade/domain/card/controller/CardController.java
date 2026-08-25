package com.pokade.domain.card.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;
import com.pokade.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "카드", description = "포켓몬 카드 검색·상세·연관 카드·필터 옵션 조회 API (비로그인 조회 가능)")
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /** #308: q(키워드, 옵션)를 추가해 필터+키워드 통합 검색을 지원한다. q가 없으면 기존과 동일하게 필터 전용 검색으로 동작한다. */
    @Operation(
            summary = "카드 검색",
            description = "키워드와 필터(타입·레어도·언어·확장팩·가격 범위)를 조합해 카드를 검색합니다. "
                    + "q를 생략하면 필터 전용 검색으로 동작합니다. sort는 latest·name·popular만 인식하며, "
                    + "그 외 값을 보내면 오류 없이 latest로 처리합니다."
    )
    @GetMapping
    public ApiResponse<Page<CardResponse>> search(
            @Parameter(description = "검색 키워드 (카드 이름 또는 포켓몬 이름)") @RequestParam(required = false) String q,
            @Parameter(description = "카드 타입") @RequestParam(required = false) List<String> types,
            @Parameter(description = "레어도") @RequestParam(required = false) List<String> rarity,
            @Parameter(description = "언어") @RequestParam(required = false) List<String> languages,
            @Parameter(description = "확장팩 ID") @RequestParam(required = false) String expansionId,
            @Parameter(description = "최소 가격") @RequestParam(required = false) Integer minPrice,
            @Parameter(description = "최대 가격") @RequestParam(required = false) Integer maxPrice,
            @Parameter(description = "정렬 기준 (latest, name, popular)") @RequestParam(required = false) String sort,
            @PageableDefault(size = CardService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(cardService.search(q, types, rarity, languages, expansionId, minPrice, maxPrice, sort, pageable));
    }

    /**
     * #308: PriceChatTools(챗봇 도메인)가 CardService.searchByKeyword(String, Pageable) 시그니처를
     * 그대로 호출 중이라 이 엔드포인트/메서드 시그니처는 유지한다. 필터가 필요한 클라이언트는
     * GET /api/cards?q=...를 쓰면 된다.
     */
    @Operation(
            summary = "카드 키워드 검색",
            description = "키워드만으로 카드를 검색하며 정렬은 이름순으로 고정됩니다. q는 필수이고 공백만 "
                    + "보내면 실패합니다. 시세 챗봇이 사용하는 엔드포인트라 시그니처를 유지합니다. "
                    + "필터가 필요하거나 키워드 없이 조회하려면 GET /api/cards 를 사용합니다."
    )
    @GetMapping("/search")
    public ApiResponse<Page<CardResponse>> searchByKeyword(
            @Parameter(required = true, description = "검색 키워드 (필수, 공백 불가)") @RequestParam(required = true) String q,
            @PageableDefault(size = CardService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(cardService.searchByKeyword(q, pageable));
    }

    @Operation(summary = "카드 상세 조회", description = "카드 한 장의 상세 정보를 조회합니다. 존재하지 않는 카드면 실패합니다.")
    @GetMapping("/{id}")
    public ApiResponse<CardDetailResponse> detail(
            @Parameter(description = "카드 ID") @PathVariable Long id) {
        return ApiResponse.ok(cardService.getDetail(id));
    }

    @Operation(
            summary = "연관 카드 조회",
            description = "같은 포켓몬(도감번호)의 다른 카드를 조회하고, 도감번호가 없으면 같은 확장팩의 카드를 "
                    + "반환합니다. 둘 다 없으면 빈 목록을 반환합니다."
    )
    @GetMapping("/{id}/related")
    public ApiResponse<List<CardResponse>> related(
            @Parameter(description = "카드 ID") @PathVariable Long id) {
        return ApiResponse.ok(cardService.getRelated(id));
    }

    @Operation(
            summary = "검색 필터 옵션 조회",
            description = "카드 검색 화면에서 사용할 필터 후보값(타입·레어도·언어·확장팩) 목록을 조회합니다."
    )
    @GetMapping("/facets")
    public ApiResponse<CardFacetsResponse> facets() {
        return ApiResponse.ok(cardService.getFacets());
    }
}
