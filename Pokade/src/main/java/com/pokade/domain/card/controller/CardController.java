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
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;
import com.pokade.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping
    public ApiResponse<Page<CardResponse>> search(
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> rarity,
            @RequestParam(required = false) List<String> grades,
            @RequestParam(required = false) String expansionId,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(cardService.search(types, rarity, grades, expansionId, sort, pageable));
    }

    @GetMapping("/search")
    public ApiResponse<Page<CardResponse>> searchByKeyword(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
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
}