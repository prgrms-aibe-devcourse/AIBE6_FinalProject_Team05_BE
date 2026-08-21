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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /** #308: q(키워드, 옵션)를 추가해 필터+키워드 통합 검색을 지원한다. q가 없으면 기존과 동일하게 필터 전용 검색으로 동작한다. */
    @GetMapping
    public ApiResponse<Page<CardResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> rarity,
            @RequestParam(required = false) List<String> languages,
            @RequestParam(required = false) String expansionId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = CardService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(cardService.search(q, types, rarity, languages, expansionId, minPrice, maxPrice, sort, pageable));
    }

    /**
     * #308: PriceChatTools(챗봇 도메인)가 CardService.searchByKeyword(String, Pageable) 시그니처를
     * 그대로 호출 중이라 이 엔드포인트/메서드 시그니처는 유지한다. 필터가 필요한 클라이언트는
     * GET /api/cards?q=...를 쓰면 된다.
     */
    @GetMapping("/search")
    public ApiResponse<Page<CardResponse>> searchByKeyword(
            @RequestParam(required = false) String q,
            @PageableDefault(size = CardService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(cardService.searchByKeyword(q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<CardDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(cardService.getDetail(id));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<CardResponse>> related(@PathVariable Long id) {
        return ApiResponse.ok(cardService.getRelated(id));
    }

    @GetMapping("/facets")
    public ApiResponse<CardFacetsResponse> facets() {
        return ApiResponse.ok(cardService.getFacets());
    }
}