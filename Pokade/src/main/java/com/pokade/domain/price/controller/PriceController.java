package com.pokade.domain.price.controller;

import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/{cardId}/summary")
    public ApiResponse<PriceSummaryResponse> getSummary(
            @PathVariable Long cardId,
            @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.ok(priceService.getSummary(cardId, variantId));
    }

    // /search 카드 그리드처럼 여러 카드의 즉시구매가/판매가를 한 번에 조회하기 위한 배치 버전.
    // 예: /api/prices/summaries?cardIds=1,2,3 — 항상 각 카드의 대표 판본 기준(variantId 지정 불가).
    @GetMapping("/summaries")
    public ApiResponse<List<CardPriceSummaryResponse>> getSummaries(@RequestParam List<Long> cardIds) {
        return ApiResponse.ok(priceService.getSummaries(cardIds));
    }

    @GetMapping("/{cardId}/trades")
    public ApiResponse<List<TradeSummaryResponse>> getRecentTrades(@PathVariable Long cardId) {
        return ApiResponse.ok(priceService.getRecentTrades(cardId));
    }

    @GetMapping("/{cardId}/chart")
    public ApiResponse<List<TradeSummaryResponse>> getPriceChart(
            @PathVariable Long cardId,
            @RequestParam String period
    ) {
        return ApiResponse.ok(priceService.getPriceChart(cardId, period));
    }
}
